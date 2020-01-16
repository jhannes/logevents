package org.logevents.config;

import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Configuration {

    private Properties properties;
    private String prefix;
    private Set<String> expectedFields = new TreeSet<>();

    public Configuration(Properties properties, String prefix) {
        this.properties = properties;
        this.prefix = prefix;
    }

    public Configuration() {
        this(new Properties(), "");
    }

    /**
     * If <code>observer.whatever.applicationName</code> or <code>observer.*.applicationName</code>
     * is set, returns that value, otherwise calculates the name of the application based on the JAR-file of
     * the main class. If run from a directory classpath, use the name of the current working directory instead
     */
    public String getApplicationName() {
        return optionalString("applicationName")
                .orElseGet(() -> optionalDefaultString("applicationName")
                    .orElseGet(Configuration::calculateApplicationName));
    }

    /**
     * Calculates the name of the application based on the JAR-file of the main class.
     * If run from a directory classpath, use the name of the current working directory instead
     */
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
                .orElseThrow(() -> new LogEventConfigurationException("Missing required key <" + fullKey(key) + "> in <" + sorted(properties.keySet()) + ">"));
    }

    private List<String> sorted(Set<Object> strings) {
        return strings.stream().map(Object::toString).sorted().collect(Collectors.toList());
    }

    public boolean getBoolean(String key) {
        return optionalString(key)
                .map(Boolean::valueOf)
                .orElse(false);
    }

    public Level getLevel(String key, Level defaultValue) {
        return optionalString(key).map(Level::valueOf).orElse(defaultValue);
    }

    public List<String> getStringList(String key) {
        return optionalString(key)
                .map(s -> Stream.of(s.split(",\\s*")).map(String::trim).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public List<String> getDefaultStringList(String key) {
        return optionalDefaultString(key)
                .map(s -> Stream.of(s.split(",\\s*")).map(String::trim).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public Optional<String> optionalString(String key) {
        expectedFields.add(key);
        String property = properties.getProperty(fullKey(key));
        return property == null || property.isEmpty() ? Optional.empty() : Optional.of(property);
    }

    public Optional<String> optionalDefaultString(String key) {
        String property = properties.getProperty(defaultKey(key));
        return property == null || property.isEmpty() ? Optional.empty() : Optional.of(property);
    }

    private String defaultKey(String key) {
        return "observer.*." + key;
    }

    public String fullKey(String key) {
        return prefix + "." + key;
    }

    public <T> T createInstance(String key, Class<T> clazz) {
        optionalString(key)
            .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + fullKey(key)));
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
        return ConfigUtil.create(fullKey(key), clazz.getPackage().getName(), properties);
    }

    public <T> T createInstance(String key, Class<T> clazz, String defaultPackage) {
        optionalString(key)
            .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + fullKey(key)));
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
        return ConfigUtil.create(fullKey(key), defaultPackage, properties);
    }

    public <T> T createInstanceWithDefault(String key, Class<T> defaultClass) {
        expectedFields.add(key);
        Class<?> clazz = ConfigUtil.getClass(fullKey(key), defaultClass.getPackage().getName(), properties)
                .orElse(defaultClass);
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
        return ConfigUtil.create(fullKey(key), clazz, properties);
    }

    public <T> T  createInstanceWithDefault(String key, Class<T> targetType, Class<? extends T> defaultClass) {
        expectedFields.add(key);
        Class<?> clazz = ConfigUtil.getClass(fullKey(key), targetType.getPackage().getName(), properties)
                .orElse(defaultClass);
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
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

    public String getServerUser() {
        return System.getProperty("user.name") + "@" + getNodeName();
    }

    public String getApplicationNode() {
        return getApplicationName() + "@" + getNodeName();
    }

    /**
     * If <code>observer.whatever.nodeName</code> or <code>observer.*.nodeName</code>
     * is set, returns that value, otherwise returns the hostname of the computer running
     * this JVM.
     */
    public String getNodeName() {
        return optionalString("nodeName")
                .orElseGet(() -> optionalDefaultString("nodeName")
                    .orElseGet(Configuration::calculateNodeName));
    }

    public static String calculateNodeName() {
        try {
            return Optional.ofNullable(System.getenv("HOSTNAME"))
                    .orElse(Optional.ofNullable(System.getenv("HTTP_HOST"))
                            .orElse(Optional.ofNullable(System.getenv("COMPUTERNAME"))
                                    .orElse(InetAddress.getLocalHost().getHostName())));
        } catch (UnknownHostException ignored) {
            return "unknown host";
        }
    }

    public List<String> getIncludedMdcKeys() {
        if (properties.getProperty(fullKey("includedMdcKeys")) != null) {
            return getStringList("includedMdcKeys");
        } else if (properties.getProperty(defaultKey("includedMdcKeys")) != null) {
            return getDefaultStringList("includedMdcKeys");
        } else {
            return null;
        }
    }

    public List<String> getPackageFilters() {
        List<String> packageFilters = getStringList("packageFilter");
        if (packageFilters.isEmpty()) {
            return getDefaultStringList("packageFilter");
        } else {
            return packageFilters;
        }
    }

    public Locale getLocale() {
        return Locale.getDefault(Locale.Category.FORMAT);
    }

    @Override
    public String toString() {
        return "Configuration{prefix='" + prefix + '\'' + '}';
    }

}
