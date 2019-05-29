package org.logevents.config;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Configuration {

    private Properties properties;
    private String prefix;
    private Set<String> expectedFields = new TreeSet<>();

    public Configuration(Properties properties, String prefix) {
        this.properties = properties;
        this.prefix = prefix;
    }

    public String getApplicationName() {
        return optionalString("application")
                .orElseGet(Configuration::calculateApplicationName);
    }

    public static String calculateApplicationName() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(pair -> pair.getKey().getName().equals("main"))
                .map(Map.Entry::getValue)
                .findAny()
                .filter(stackTrace -> (!isRunningInTest(stackTrace)))
                .map(stackTrace -> determineJarName(stackTrace[stackTrace.length - 1].getClassName()))
                .orElseGet(Configuration::currentWorkingDirectory);
    }

    public static boolean isRunningInTest() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(pair -> pair.getKey().getName().equals("main"))
                .map(Map.Entry::getValue)
                .findAny()
                .filter(Configuration::isRunningInTest)
                .isPresent();
    }

    private static boolean isRunningInTest(StackTraceElement[] stackTrace) {
        return Arrays.stream(stackTrace)
                .anyMatch(ste -> ste.getClassName().startsWith("org.junit.runners."));
    }

    static String determineJarName(String className) {
        try {
            return Optional.ofNullable(Class.forName(className).getProtectionDomain().getCodeSource())
                    .map(codeSource -> codeSource.getLocation().getPath())
                    .filter(path -> !path.endsWith("/"))
                    .map(Configuration::toApplicationName)
                    .orElseGet(Configuration::currentWorkingDirectory);
        } catch (ClassNotFoundException e) {
            return currentWorkingDirectory();
        }
    }

    private static String currentWorkingDirectory() {
        return Paths.get("").toAbsolutePath().getFileName().toString();
    }

    /** Remove directory name, .jar suffix and semver version from file path */
    static String toApplicationName(String jarPath) {
        int lastSlash = jarPath.lastIndexOf('/');
        String filename = jarPath.substring(lastSlash + 1);
        return filename
                .replaceAll("(-\\d+(\\.\\d+)*(-[0-9A-Za-z-.]+)?)?\\.jar$", "");
    }

    public URL getUrl(String key) {
        return toUrl(key, getString(key));
    }

    private URL toUrl(String key, String string) {
        try {
            return new URL(string);
        } catch (MalformedURLException e) {
            throw new LogEventConfigurationException(fullKey(key) + " value " + string + ": " + e.getMessage());
        }
    }

    public Optional<URL> optionalUrl(String key) {
        return optionalString(key).map(s -> toUrl(key, s));
    }


    public Optional<Integer> optionalInt(String key) {
        return optionalString(key).map(Integer::parseInt);
    }

    public Duration getDuration(String key) {
        try {
            return Duration.parse(getString(key));
        } catch (DateTimeParseException e) {
            throw new LogEventConfigurationException(fullKey(key) + " value " + getString(key) + ": " + e.getMessage());
        }
    }

    public Optional<Duration> optionalDuration(String key) {
        try {
            return optionalString(key).map(Duration::parse);
        } catch (DateTimeParseException e) {
            throw new LogEventConfigurationException(fullKey(key) + " value " + getString(key) + ": " + e.getMessage());
        }
    }

    public String getString(String key) {
        return optionalString(key)
                .orElseThrow(() -> new LogEventConfigurationException("Missing required key <" + fullKey(key) + "> in <" + properties.keySet() + ">"));
    }

    public boolean getBoolean(String key) {
        return optionalString(key)
                .map(Boolean::valueOf)
                .orElse(false);
    }

    public String[] getStringList(String key) {
        return optionalString(key)
                .map(s -> s.split(",\\s*"))
                .orElse(new String[0]);
    }

    public Optional<String> optionalString(String key) {
        expectedFields.add(key);
        String property = properties.getProperty(fullKey(key));
        return property == null || property.isEmpty() ? Optional.empty() : Optional.of(property);
    }

    public String[] getDefaultStringList(String key) {
        return optionalDefaultString(key)
                .map(s -> s.split(",\\s*"))
                .orElse(new String[0]);
    }

    public Optional<String> optionalDefaultString(String key) {
        return Optional.ofNullable(properties.getProperty(defaultKey(key)));
    }

    private String defaultKey(String key) {
        return "observer.*." + key;
    }

    private String fullKey(String key) {
        return prefix + "." + key;
    }

    public <T> T createInstance(String key, Class<T> clazz) {
        optionalString(key)
            .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + fullKey(key)));
        return ConfigUtil.create(fullKey(key), clazz.getPackage().getName(), properties);
    }

    public <T> T createInstance(String key, Class<T> clazz, String defaultPackage) {
        optionalString(key)
            .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + fullKey(key)));
        return ConfigUtil.create(fullKey(key), defaultPackage, properties);
    }

    public <T> T createInstanceWithDefault(String key, Class<T> defaultClass) {
        expectedFields.add(key);
        Class<?> clazz = ConfigUtil.getClass(fullKey(key), defaultClass.getPackage().getName(), properties)
                .orElse(defaultClass);
        return ConfigUtil.create(fullKey(key), clazz, properties);
    }

    public <T> T  createInstanceWithDefault(String key, Class<T> targetType, Class<? extends T> defaultClass) {
        expectedFields.add(key);
        Class<?> clazz = ConfigUtil.getClass(fullKey(key), targetType.getPackage().getName(), properties)
                .orElse(defaultClass);
        return ConfigUtil.create(fullKey(key), clazz, properties);
    }

    public String getPrefix() {
        return prefix;
    }

    public void checkForUnknownFields() {
        Set<String> remainingFields = properties.stringPropertyNames().stream()
                .filter(n -> n.startsWith(prefix + "."))
                .map(n -> n.substring(prefix.length() + 1))
                .map(n -> n.replaceAll("(\\w+)*.*", "$1"))
                .collect(Collectors.toCollection(TreeSet::new));
        remainingFields.removeAll(expectedFields);
        if (!remainingFields.isEmpty()) {
            throw new LogEventConfigurationException(
                    String.format("Unknown configuration options: %s for %s. Expected options: %s", remainingFields, prefix, expectedFields));
        }

    }

    /**
     * List all direct property names under the specified key. For example,
     * if a Configuration with prefix "observer.test" has properties
     * "observer.test.markers.a.foo", "observer.test.markers.a.bar" and
     * "observer.test.markers.b", <code>listProperties("markers")</code>
     * will return ["a", "b"].
     */
    public Set<String> listProperties(String key) {
        expectedFields.add(key);
        String keyPrefix = prefix + "."  + key + ".";
        return properties.stringPropertyNames().stream()
                .filter(n -> n.startsWith(keyPrefix))
                .map(n -> n.substring(keyPrefix.length()))
                .map(n -> n.split("\\.")[0])
                .collect(Collectors.toSet());
    }

    public String getNodeName() {
        return optionalString("nodeName")
                .orElseGet(Configuration::calculateNodeName);
    }

    public static String calculateNodeName() {
        String hostname = "unknown host";
        try {
            hostname = Optional.ofNullable(System.getenv("HOSTNAME"))
                    .orElse(Optional.ofNullable(System.getenv("HTTP_HOST"))
                            .orElse(Optional.ofNullable(System.getenv("COMPUTERNAME"))
                                    .orElse(InetAddress.getLocalHost().getHostName())));
        } catch (UnknownHostException ignored) {
        }

        String username = System.getProperty("user.name");
        return username + "@" + hostname;
    }
}
