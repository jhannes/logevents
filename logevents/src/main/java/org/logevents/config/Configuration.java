package org.logevents.config;

import org.logevents.LogEventObserver;
import org.logevents.LogEventFormatter;
import org.logevents.status.LogEventStatus;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to configure {@link org.logevents.LogEventObserver} instances. Instantiate {@link Configuration}
 * with {@link Map} and a String prefix and get values with {@link #getString}
 * and {@link #optionalString}. Values are read using the prefix + the key given to {@link #getString},
 * or from environment variables. E.g. if prefix is "observer.console", <code>getString("threshold")</code>
 * looks for property "observer.console.threshold" or environment variable "LOGEVENTS_OBSERVER_CONSOLE_THRESHOLD".
 *
 * <p>Use {@link #createInstance} to create objects based on configuration, and special methods
 * {@link #getMdcFilter()}, {@link #getPackageFilter()}, {@link #getApplicationName()}, {@link #getNodeName()}
 * and {@link #getApplicationNode()} to read commonly used configuration. Use {@link #optionalGlobalString(String)}
 * to read values that may be configured for several observers at once.</p>
 */
public class Configuration {

    private static final Optional<String> mainClassName = calculateMainClassName();
    private static final String defaultApplicationName = calculateApplicationName(mainClassName);
    private static final String defaultNodeName = calculateNodeName();
    static final String[] DEFAULT_PACKAGE_FILTER = {
            "sun.net.www", "java.util.stream", "sun.net.www.protocol.https",
            "sun.nio.fs", "sun.reflect.", "jdk.internal.reflect", "org.junit.",
            "com.intellij.junit", "com.intellij.rt"
    };

    private final Map<String, String> properties;
    private final String prefix;
    private final Set<String> expectedFields = new TreeSet<>();
    private final Map<String, String> environment;

    public Configuration(Map<String, String> properties, String prefix) {
        this(properties, prefix, System.getenv());
    }

    public Configuration(Properties properties, String prefix) {
        this((Map<String, String>) (Map<?, ?>) properties, prefix, System.getenv());
    }

    public Configuration(Map<String, String> properties, String prefix, Map<String, String> environment) {
        this.properties = properties;
        this.prefix = prefix;
        this.environment = environment;
    }

    public Configuration() {
        this(new HashMap<>(), "");
    }

    /**
     * Checks if all non-empty values in {@link #properties} prefixed with {@link #prefix} have been
     * requested with {@link #optionalString} (or a method using it) and throws an exception with
     * any unused properties. When implementing an observer, call this method after you have read
     * the whole configuration to alert the user of misconfiguration
     */
    public void checkForUnknownFields() {
        Set<String> remainingFields = properties.keySet().stream()
                .filter(n -> n.startsWith(prefix + "."))
                .filter(n -> !properties.get(n).trim().isEmpty())
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
     * Returns true if this key (prefixed with the current context) is either in the configuration properties
     * or an environment variable
     */
    public boolean containsKey(String key) {
        expectedFields.add(key);
        return properties.containsKey(prefixedKey(key)) || environment.containsKey(getEnvironmentKey(prefixedKey(key)));
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
        String keyPrefix = prefix + "." + key + ".";
        return properties.keySet().stream()
                .filter(n -> n.startsWith(keyPrefix))
                .map(n -> n.substring(keyPrefix.length()))
                .map(n -> n.split("\\.")[0])
                .collect(Collectors.toSet());
    }

    /**
     * Returns prefixed value from properties or environment. For example, if {@link #prefix} is
     * "observer.console", <code>optionalString("threshold")</code> will check property value
     * "observer.console.threshold" and environment variable "LOGEVENTS_OBSERVER_CONSOLE_THRESHOLD"
     */
    public Optional<String> optionalString(String key) {
        expectedFields.add(key);
        return getProperty(prefixedKey(key));
    }

    /**
     * Returns global value from properties or environment. For example, <code>optionalString("threshold")</code>
     * will check property value "observer.*.threshold" and environment variable "LOGEVENTS_THRESHOLD"
     */
    public Optional<String> optionalGlobalString(String key) {
        Optional<String> result = Optional.ofNullable(properties.get(globalKey(key)));
        return (result.isPresent() ? result : getPropertyFromEnvironment(key)).filter(s -> !s.isEmpty());
    }

    /**
     * Returns prefixed or global value from properties or environment. For example, if {@link #prefix} is
     * "observer.console", <code>optionalStringOrGlobal("threshold")</code> will check property values
     * "observer.console.threshold" and "observer.*.threshold" and environment variables
     * "LOGEVENTS_OBSERVER_CONSOLE_THRESHOLD" and "LOGEVENTS_CONSOLE".
     */
    public Optional<String> optionalStringOrGlobal(String key) {
        Optional<String> property = optionalString(key);
        return property.isPresent() ? property : optionalGlobalString(key);
    }

    /**
     * Convenience method for {@link #optionalString(String)} which throws {@link LogEventConfigurationException}
     * if key is missing.
     */
    public String getString(String key) {
        return optionalString(key)
                .orElseThrow(() -> new LogEventConfigurationException("Missing required key <" + prefixedKey(key) + "> in <" + sorted(properties.keySet()) + ">"));
    }

    public boolean getBoolean(String key) {
        return optionalString(key).map(Boolean::valueOf).orElse(false);
    }

    public URL getUrl(String key) {
        return toUrl(key, getString(key));
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
            throw new LogEventConfigurationException(prefixedKey(key) + " value " + getString(key) + ": " + e.getMessage());
        }
    }

    public Optional<Duration> optionalDuration(String key) {
        try {
            return optionalString(key).map(Duration::parse);
        } catch (DateTimeParseException e) {
            throw new LogEventConfigurationException(prefixedKey(key) + " value " + getString(key) + ": " + e.getMessage());
        }
    }

    public List<String> getStringList(String key) {
        return toStringList(optionalString(key));
    }

    public List<String> getGlobalStringList(String key) {
        return toStringList(optionalGlobalString(key));
    }

    public List<String> getStringListOrGlobal(String key) {
        return optionalStringOrGlobal(key).map(this::toStringList).orElse(null);
    }

    private Optional<String> getProperty(String key) {
        Optional<String> result = Optional.ofNullable(properties.get(key));
        return result.isPresent() ? result : getPropertyFromEnvironment(key);
    }

    private Optional<String> getPropertyFromEnvironment(String key) {
        return Optional.ofNullable(environment.get(getEnvironmentKey(key)));
    }

    private String globalKey(String key) {
        return "observer.*." + key;
    }

    public String prefixedKey(String key) {
        return prefix + "." + key;
    }

    private String getEnvironmentKey(String key) {
        return (key.startsWith("logevents.") ? "" : "LOGEVENTS_") + key.toUpperCase().replace('.', '_');
    }

    /**
     * Instantiates a class with the name in the provided property name and verifies that it's
     * a subtype of the argument class. If the class has a constructor with String, Properties,
     * this is invoked with the argument key added to the current prefix. Otherwise, the default
     * constructor is used.
     */
    public <T> T createInstance(String key, Class<T> clazz) {
        optionalString(key)
                .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + prefixedKey(key)));
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
        return ConfigUtil.create(prefixedKey(key), clazz.getPackage().getName(), optionalString(key), properties);
    }

    /**
     * Instantiates a class with the name in the provided property name. If the configured class name doesn't have
     * a package name, defaultPackage.
     *
     * @see #createInstance(String, Class)
     */
    public <T> T createInstance(String key, Class<T> clazz, String defaultPackage) {
        optionalString(key)
                .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + prefixedKey(key)));
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
        return ConfigUtil.create(prefixedKey(key), defaultPackage, optionalString(key), properties);
    }

    /**
     * Instantiates a class with the name in the provided property name and verifies that it's
     * a subclass of the defaultClass. If no property is configured, the defaultClass is used instead
     *
     * @see #createInstance(String, Class)
     */
    @SuppressWarnings("unchecked")
    public <T> T createInstanceWithDefault(String key, Class<T> defaultClass) {
        Class<T> clazz = optionalString(key)
                .map(c -> (Class<T>) ConfigUtil.getClass(prefixedKey(key), defaultClass.getPackage().getName(), c))
                .orElse(defaultClass);
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
        return ConfigUtil.create(prefixedKey(key), clazz, properties);
    }

    /**
     * Instantiates a class with the name in the provided property name and verifies that it's
     * a subtype of the targetType. If no property is configured, the defaultClass is used instead
     *
     * @see #createInstance(String, Class)
     */
    public <T> T createInstanceOrGlobal(String key, Class<T> targetType, Class<? extends T> defaultClass) {
        Optional<String> specificClass = optionalString(key);
        if (specificClass.isPresent()) {
            return ConfigUtil.create(
                    prefixedKey(key),
                    ConfigUtil.getClass(prefixedKey(key), targetType.getPackage().getName(), specificClass.get()),
                    properties
            );
        }
        Optional<String> globalClass = optionalGlobalString(key);
        if (globalClass.isPresent()) {
            LogEventStatus.getInstance().addDebug(this, "Creating " + globalKey(key));
            return ConfigUtil.create(
                    globalKey(key),
                    ConfigUtil.getClass(globalKey(key), targetType.getPackage().getName(), globalClass.get()),
                    properties
            );
        }
        LogEventStatus.getInstance().addDebug(this, "Creating " + prefixedKey(key));
        return ConfigUtil.create(prefixedKey(key), defaultClass, properties);
    }

    public <T> T createInstanceWithDefault(String key, Class<T> targetType, Class<? extends T> defaultClass) {
        return createInstanceWithDefault(key, defaultClass, targetType.getPackage().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T createInstanceWithDefault(String key, Class<? extends T> defaultClass, String packageName) {
        Class<T> clazz = optionalString(key)
                .map(c -> (Class<T>) ConfigUtil.getClass(prefixedKey(key), packageName, c))
                .orElseGet(() -> (Class) defaultClass);
        LogEventStatus.getInstance().addDebug(this, "Creating " + key);
        return ConfigUtil.create(prefixedKey(key), clazz, properties);
    }

    public LogEventFormatter createFormatter(String key, Class<? extends LogEventFormatter> defaultClass) {
        return createInstanceWithDefault(key, defaultClass, "org.logevents.formatters");
    }

    public LogEventObserver createObserver(String key) {
        return createInstance(key, LogEventObserver.class, "org.logevents.observers");
    }

    /**
     * Returns a filter to determine which MDC variables to include in input. These are set either
     * with <code>observer.&lt;name&gt;.includedMdcKeys</code>, <code>observer.&lt;name&gt;.excludedMdcKeys</code>,
     * <code>observer.*.includedMdcKeys</code>, <code>observer.*.excludedMdcKeys</code>. Only one of the options
     * is used and inclusion is preferred over exclusion.
     */
    public MdcFilter getMdcFilter() {
        List<String> includedMdcKeys = getStringListOrGlobal("includedMdcKeys");
        List<String> excludedMdcKeys = getStringListOrGlobal("excludedMdcKeys");
        if (includedMdcKeys != null) {
            return includedMdcKeys::contains;
        } else if (excludedMdcKeys != null) {
            return key -> !excludedMdcKeys.contains(key);
        } else {
            return key -> true;
        }
    }

    /**
     * Returns a list of classname prefixes that should be removed from stack traces
     */
    public List<String> getPackageFilter() {
        List<String> packageFilter = getStringList("packageFilter");
        if (!packageFilter.isEmpty()) {
            return packageFilter;
        } else if (!getGlobalStringList("packageFilter").isEmpty()) {
            return getGlobalStringList("packageFilter");
        } else {
            return Arrays.asList(DEFAULT_PACKAGE_FILTER);
        }
    }

    public Locale getLocale() {
        return Locale.getDefault(Locale.Category.FORMAT);
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
                .orElseGet(() -> optionalGlobalString("nodeName").orElse(defaultNodeName));
    }

    /**
     * If <code>observer.whatever.applicationName</code> or <code>observer.*.applicationName</code>
     * is set, returns that value, otherwise calculates the name of the application based on the JAR-file of
     * the main class. If run from a directory classpath, use the name of the current working directory instead
     */
    public String getApplicationName() {
        return optionalString("applicationName")
                .orElseGet(() -> optionalGlobalString("applicationName").orElse(defaultApplicationName));
    }

    /**
     * Calculates the name of the application based on the JAR-file of the main class.
     * If run from a directory classpath, use the name of the current working directory instead
     */
    private static Optional<String> calculateMainClassName() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(pair -> pair.getKey().getName().equals("main"))
                .map(Map.Entry::getValue)
                .findAny()
                .map(stackTrace -> stackTrace[stackTrace.length - 1].getClassName());
    }

    /**
     * Calculates the name of the application based on the JAR-file of the main class.
     * If run from a directory classpath, use the name of the current working directory instead
     */
    private static String calculateApplicationName(Optional<String> mainClassName) {
        if (isRunningInTest()) {
            return currentWorkingDirectory();
        }
        return mainClassName.map(Configuration::determineJarName).orElseGet(Configuration::currentWorkingDirectory);
    }

    public static Optional<String> getMainClassName() {
        return mainClassName;
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

    /**
     * Remove directory name, .jar suffix and semver version from file path
     */
    static String toApplicationName(String jarPath) {
        int lastSlash = jarPath.lastIndexOf('/');
        String filename = jarPath.substring(lastSlash + 1);
        return filename
                .replaceAll("(-\\d+(\\.\\d+)*(-[0-9A-Za-z-.]+)?)?\\.jar$", "");
    }

    private URL toUrl(String key, String string) {
        try {
            return new URL(string);
        } catch (MalformedURLException e) {
            throw new LogEventConfigurationException(prefixedKey(key) + " value " + string + ": " + e.getMessage());
        }
    }

    private List<String> sorted(Set<?> strings) {
        return strings.stream().map(Object::toString).sorted().collect(Collectors.toList());
    }

    private List<String> toStringList(Optional<String> value) {
        return value.map(this::toStringList).orElse(Collections.emptyList());
    }

    private List<String> toStringList(String s) {
        return Stream.of(s.split(",\\s*")).map(String::trim).collect(Collectors.toList());
    }

    public String getPrefix() {
        return prefix;
    }


    private static String calculateNodeName() {
        try {
            return Optional.ofNullable(System.getenv("HOSTNAME"))
                    .orElse(Optional.ofNullable(System.getenv("HTTP_HOST"))
                            .orElse(Optional.ofNullable(System.getenv("COMPUTERNAME"))
                                    .orElse(InetAddress.getLocalHost().getHostName())));
        } catch (UnknownHostException ignored) {
            return "unknown host";
        }
    }

    @Override
    public String toString() {
        return "Configuration{prefix='" + prefix + '\'' + '}';
    }

}
