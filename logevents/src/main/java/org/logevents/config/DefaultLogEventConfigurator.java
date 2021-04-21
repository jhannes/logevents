package org.logevents.config;

import org.logevents.LogEventConfigurator;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LoggerConfiguration;
import org.logevents.impl.LevelThresholdFilter;
import org.logevents.impl.LogEventFilter;
import org.logevents.jmx.LogEventsMBeanFactory;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.impl.ConditionalLogEventFilter;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.FileLogEventObserver;
import org.logevents.observers.LevelThresholdConditionalObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * <p>You can extends this class and put the classname your configurator in
 * <code>META-INF/services/org.logevents.LogEventConfigurator</code> override the default behavior.</p>
 *
 * Default implementation of {@link LogEventConfigurator} which reads the
 * logging configuration from <code>logevents.properties</code>-files in the
 * directory specified by the system property <strong><code>logevents.directory</code></strong>
 * (defaults to current working directory).
 * This class will by default load <code>logevents-<em>profile</em>.properties</code>
 * and <code>logevents.properties</code>, where based on {@link #getProfiles()}.
 * <code>logevents.properties</code> files in profile takes precedence over default
 * <code>logevents.properties</code> files. Files in configuration directory takes
 * precedence over files in classpath and are automatically scanned for changes.
 * <p>
 * <code>logevents.properties</code> should look like this ({@link #applyConfigurationProperties}):
 *
 * <pre>
 * root=LEVEL [observer1,observer2]
 * root.observer.observer3=LEVEL
 *
 * logger.com.example=LEVEL [observer]
 * includeParent.com.example=true|false
 *
 * observer.name=ObserverClass
 * observer.name.property=value
 * </pre>
 * <p>By default, observers named <code>observer.console</code> {@link ConsoleLogEventObserver} and
 * <code>observer.file</code> {@link FileLogEventObserver} are created. By default, the <code>root</code>
 * root logger is set to <code>INFO console</code>.</p>
 *
 * <h3>Log configuration can also be loaded from environment variables:</h3>
 *
 * <ul>
 *     <li><strong>LOGEVENTS_STATUS</strong>:
 *      {@link org.logevents.status.StatusEvent.StatusLevel} for internal diagnostics
 *     </li>
 *     <li><strong>LOGEVENTS_ROOT=&lt;LEVEL [logger1,logger2,...]&gt;</strong>:
 *      The root level and observer, for example <code>DEBUG console</code></li>
 *     <li><strong>LOGEVENTS_LOGGER_ORG_EXAMPLE=&lt;LEVEL [logger1,logger2,...]&gt;</strong>:
 *      The level and observer for the case-insensitive logger category, for example <code>DEBUG console</code></li>
 *     <li><strong>LOGEVENTS_ROOT_OBSERVER_&lt;observerName&gt;=&lt;LEVEL&gt;</strong>:
 *     Add a global observer, for example <code>LOGEVENTS_ROOT_OBSERVER_STATS=DEBUG</code></li>
 *     <li><strong>LOGEVENT_OBSERVER_&lt;observerName&gt;=&lt;ClassName&gt;</strong>: initialize observer</li>
 *     <li><strong>LOGEVENT_OBSERVER_&lt;observerName&gt;_PROPERTY</strong>: observer configuration</li>
 * </ul>
 *
 * <p>
 * This class is designed with subclassing in mind. When subclassing,
 * {@link #configure(LogEventFactory)} should probably be overridden
 *
 * @author Johannes Brodwall
 *
 */
public class DefaultLogEventConfigurator implements LogEventConfigurator {

    public static final String WELCOME_MESSAGE = "Logging by LogEvents (http://logevents.org). Create a file logevents.properties with the line logevents.status=INFO to suppress this message";
    private final Path propertiesDir;
    private Thread configurationWatcher;
    private boolean runningInsideJunit;

    public DefaultLogEventConfigurator(Path propertiesDir) {
        this.propertiesDir = propertiesDir;
    }

    public DefaultLogEventConfigurator() {
        this(Paths.get(System.getProperty("logevents.directory", ".")).toAbsolutePath().normalize());
    }

    /**
     * Implementation of {@link LogEventConfigurator#configure(LogEventFactory)}.
     * Suitable for overriding.
     */
    @Override
    public void configure(LogEventFactory factory) {
        runningInsideJunit = factory.isRunningInsideJunit();
        resetConfigurationFromFiles(factory);
        startConfigurationFileWatcher(factory);
    }

    /**
     * Starts a thread which watches the configurator's propertiesDir for changes
     * and resets the configuration from files when something is changed.
     *
     * @param factory The LogEventFactory that this configurator should configure
     */
    protected void startConfigurationFileWatcher(LogEventFactory factory) {
        try {
            WatchService watchService = propertiesDir.getFileSystem().newWatchService();
            propertiesDir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

            configurationWatcher = new Thread(() -> {
                try {
                    while (true) {
                        WatchKey key = watchService.take();
                        boolean shouldReload = false;
                        List<String> fileNames = getConfigurationFileNames();
                        //noinspection BusyWait: Short pause to queue up simultaneous events
                        Thread.sleep(50);
                        for (WatchEvent<?> watchEvent : key.pollEvents()) {
                            Path context = (Path)watchEvent.context();
                            if (fileNames.contains(context.getFileName().toString())) {
                                shouldReload = true;
                            }
                        }
                        key.reset();
                        if (shouldReload) {
                            LogEventStatus.getInstance().addInfo(DefaultLogEventConfigurator.this, "Reloading configuration");
                            resetConfigurationFromFiles(factory);
                        }
                    }
                } catch (InterruptedException e) {
                    LogEventStatus.getInstance().addConfig(DefaultLogEventConfigurator.this,
                            this + " interrupted, exiting");
                }
            });
            configurationWatcher.setName("Logevents-configuration-watcher");
            configurationWatcher.setDaemon(true);
            configurationWatcher.start();
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Could not start file watcher", e);
        }
    }

    void stopFileScanner() {
        if (configurationWatcher != null) {
            configurationWatcher.interrupt();
        }
    }

    /**
     * Read configuration from the default configuration file based on
     * {@link #getProfiles()}.
     *
     * @param factory The LogEventFactory that this configurator should configure
     */
    protected synchronized void resetConfigurationFromFiles(LogEventFactory factory) {
        try {
            applyConfigurationProperties(factory, loadConfigurationProperties());
        } catch (Exception e) {
            LogEventStatus.getInstance().addFatal(this, "Failed to load " + getConfigurationFileNames(), e);
            factory.setObservers(new HashMap<>());
            factory.setRootLevel(getDefaultRootLevel());
            factory.setRootObserver(new ConsoleLogEventObserver());
        }
    }

    protected Level getDefaultRootLevel() {
        return Level.INFO;
    }

    /**
     * Reads <code>logevents.properties</code> from classpath and filesystem.
     * {@link #getProfiles()} is used to determine <code>logevents-<em>profile</em>.properties</code>
     * files names. Files from profile take precedence over default <code>logevents.properties</code>
     * and files from filesystem takes precedence over files from classpath.
     *
     * @return A merged Properties object with all relevant files merged together
     */
    public Properties loadConfigurationProperties() {
        return loadPropertiesFromFiles(getConfigurationFileNames(), new Properties());
    }

    /**
     * Uses system properties 'profiles', 'profile' and 'spring.profiles.active'
     * (in order of preference) to determine which profiles
     * are active in the current environment. The profiles are split on ","
     *
     * @return the activated profiles for the current JVM as a list of strings
     */
    protected List<String> getProfiles() {
        List<String> profiles = new ArrayList<>();
        Optional.ofNullable(System.getenv("PROFILES"))
                .map(s -> s.split(","))
                .ifPresent(p -> profiles.addAll(Arrays.asList(p)));
        Optional.ofNullable(System.getenv("PROFILE"))
                .map(s -> s.split(","))
                .ifPresent(p -> profiles.addAll(Arrays.asList(p)));
        Optional.ofNullable(System.getProperty("profiles"))
                .map(s -> s.split(","))
                .ifPresent(p -> profiles.addAll(Arrays.asList(p)));
        Optional.ofNullable(System.getProperty("profile"))
                .map(s -> s.split(","))
                .ifPresent(p -> profiles.addAll(Arrays.asList(p)));
        Optional.ofNullable(System.getProperty("spring.profiles.active"))
                .map(s -> s.split(","))
                .ifPresent(p -> profiles.addAll(Arrays.asList(p)));
        if (runningInsideJunit) {
            profiles.add("test");
        }
        return profiles;
    }

    /**
     * Loads all properties files relevant for the argument set of profiles
     * from classpath and current working directory.
     *
     * @param configurationFileNames the file names to load
     * @param properties the Properties object to load into
     * @return Properties with the configuration of all files merged together
     */
    protected Properties loadPropertiesFromFiles(List<String> configurationFileNames, Properties properties) {
        LogEventStatus.getInstance().addConfig(this, "Loading configuration from " + configurationFileNames);
        for (String filename : configurationFileNames) {
            loadConfigResource(properties, filename);
            loadConfigFile(properties, filename);
        }
        return properties;
    }

    /**
     * Gets the corresponding configuration file names for the active profiles
     *
     * @return the file names for candidate configuration files or this JVM
     */
    protected List<String> getConfigurationFileNames() {
        List<String> result = new ArrayList<>();
        result.add("logevents.properties");
        for (String profile : getProfiles()) {
            if (!profile.isEmpty()) {
                result.add(String.format("logevents-%s.properties", profile));
            }
        }
        return result;
    }

    /**
     * Inserts properties from the file into the properties if it exists in
     * {@link DefaultLogEventConfigurator#propertiesDir}
     *
     * @param properties The destination to read the configuration into
     * @param fileName The filename to load from disk
     */
    protected void loadConfigFile(Properties properties, String fileName) {
        if (Files.isRegularFile(this.propertiesDir.resolve(fileName))) {
            try (InputStream propertiesFile = new FileInputStream(this.propertiesDir.resolve(fileName).toFile())) {
                LogEventStatus.getInstance().addDebug(this, "Loading file:" + propertiesDir.resolve(fileName));
                properties.load(propertiesFile);
            } catch (FileNotFoundException ignored) {
                // Can happen if the file is deleted after the if-check
            } catch (IOException e) {
                LogEventStatus.getInstance().addError(this, "Can't load " + fileName, e);
            }
        }
    }

    /**
     * Inserts properties from the resource name on the classpath into the properties.
     *
     * @param properties The destination to read the configuration into
     * @param resourceName The resource to load from classpath
     */
    protected void loadConfigResource(Properties properties, String resourceName) {
        try (InputStream propertiesFile = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (propertiesFile != null) {
                LogEventStatus.getInstance().addDebug(this, "Loading classpath:" + resourceName);
                properties.load(propertiesFile);
            }
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Can't load " + resourceName, e);
        }
    }

    /**
     * Configures observers and loggers from properties. Observers have
     * properties on the format <code>observers.prefix=ClassName</code> and loggers have
     * properties on the format <code>logger.log.name=LEVEL observer1,observer2</code>.
     * {@link LogEventObserver} class names must be fully qualified
     * (e.g. <code>observer.mine=com.example.MyObserver</code>)
     * unless the observer is in <code>org.logevents.observers</code> package
     * (e.g. <code>observer.slack=SlackLogEventObserver</code>).
     *
     * @param factory The LogEventFactory that this configurator should configure
     * @param properties The merged configuration that should be applied to the factory
     */
    public void applyConfigurationProperties(LogEventFactory factory, Properties properties) {
        applyConfigurationProperties(factory, properties, System.getenv());
    }

    protected void applyConfigurationProperties(LogEventFactory factory, Properties properties, Map<String, String> environment) {
        Configuration logeventsConfig = new Configuration(properties, "logevents", environment);
        LogEventStatus.getInstance().configure(logeventsConfig);
        showWelcomeMessage(properties);

        factory.setObservers(configureObservers(properties, environment));
        configureRootLogger(factory, properties, environment);
        configureLoggers(factory, properties, environment);
        installUncaughtExceptionHandler(factory, logeventsConfig);
        installJmxAdaptor(factory, logeventsConfig);
        logeventsConfig.checkForUnknownFields();
    }

    private static LogEventsMBeanFactory mbeanFactory;

    protected void installJmxAdaptor(LogEventFactory factory, Configuration config) {
        if (!config.getBoolean("jmx") && mbeanFactory == null) {
            return;
        } else if (mbeanFactory == null) {
            mbeanFactory = new LogEventsMBeanFactory();
        }
        mbeanFactory.setup(factory, this, config);
    }

    private Map<String, Supplier<? extends LogEventObserver>> configureObservers(Properties configuration, Map<String, String> environment) {
        Map<String, Supplier<? extends LogEventObserver>> observers = new HashMap<>();
        readObservers(configuration, observers, environment);
        installDefaultObservers(configuration, observers, environment);
        return observers;
    }

    private void installUncaughtExceptionHandler(LogEventFactory factory, Configuration logeventsConfig) {
        if (logeventsConfig.getBoolean("installExceptionHandler")) {
            if (Thread.getDefaultUncaughtExceptionHandler() == null) {
                Thread.setDefaultUncaughtExceptionHandler((thread, e) ->
                        factory.getRootLogger().error("Thread {} terminated with unhandled exception", thread.getName(), e));
                LogEventStatus.getInstance().addConfig(this, "Installing default uncaught exception handler");
            } else {
                LogEventStatus.getInstance().addDebug(this, "Uncaught exception handler already set to " + Thread.getDefaultUncaughtExceptionHandler());
            }
        }
    }

    private void readObservers(Properties properties, Map<String, Supplier<? extends LogEventObserver>> observers, Map<String, String> environment) {
        for (Object key : properties.keySet()) {
            if (key.toString().matches("observer\\.\\w+")) {
                String name = key.toString().substring("observer.".length());
                configureObserver(observers, name, properties.getProperty(key.toString()), properties);
            }
        }
        for (String key : environment.keySet()) {
            if (key.matches("LOGEVENTS_OBSERVER_[A-Z]+")) {
                String name = key.substring("LOGEVENTS_OBSERVER_".length());
                configureObserver(observers, name, environment.get(key), properties);
            }
        }
    }

    protected void configureLoggers(LogEventFactory factory, Properties configuration, Map<String, String> environment) {
        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("logger.")) {
                String loggerName = key.toString().substring("logger.".length());
                boolean includeParent = !"false".equalsIgnoreCase(configuration.getProperty("includeParent." + loggerName));
                String loggerConfig = configuration.getProperty(key.toString());
                if (loggerConfig != null) {
                    configureLogger(factory, factory.getLogger(loggerName), loggerConfig, includeParent);
                }
            }
        }
        for (String key : environment.keySet()) {
            if (key.startsWith("LOGEVENTS_LOGGER_")) {
                String loggerName = key.substring("LOGEVENTS_LOGGER_".length());
                boolean includeParent = !"false".equalsIgnoreCase(environment.get("LOGEVENTS_INCLUDEPARENT_" + loggerName));
                String loggerConfig = environment.get(key);
                if (loggerConfig != null) {
                    configureLogger(factory, factory.getLogger(loggerName.replace('_', '.')), loggerConfig, includeParent);
                }
            }
        }
    }

    protected void installDefaultObservers(Properties configuration, Map<String, Supplier<? extends LogEventObserver>> observers, Map<String, String> environment) {
        observers.putIfAbsent(
                "console",
                () -> createConsoleLogEventObserver(new Configuration(configuration, "observer.console", environment))
        );
        observers.putIfAbsent(
                "file",
                () -> createFileObserver(new Configuration(configuration, "observer.file", environment))
        );
    }

    protected void showWelcomeMessage(Properties configuration) {
        if (configuration.isEmpty()) {
            LogEventStatus.getInstance().addInfo(this, WELCOME_MESSAGE);
        }
    }

    protected FileLogEventObserver createFileObserver(Configuration configuration) {
        LogEventStatus.getInstance().addDebug(this, "Configuring observer.console");
        FileLogEventObserver observer = new FileLogEventObserver(configuration);
        LogEventStatus.getInstance().addDebug(this, "Configured " + observer);
        return observer;
    }

    protected ConsoleLogEventObserver createConsoleLogEventObserver(Configuration configuration) {
        LogEventStatus.getInstance().addDebug(this, "Configuring observer.console");
        ConsoleLogEventObserver observer = new ConsoleLogEventObserver(configuration);
        LogEventStatus.getInstance().addDebug(this, "Configured " + observer);
        return observer;
    }

    protected void configureRootLogger(LogEventFactory factory, Properties properties, Map<String, String> environment) {
        LinkedHashSet<LogEventObserver> observerSet = new LinkedHashSet<>();

        String rootConfiguration = properties.getProperty("root");
        if (rootConfiguration != null) {
            configureRootLogger(factory, observerSet, rootConfiguration);
        } else if (environment.containsKey("LOGEVENTS_ROOT")) {
            configureRootLogger(factory, observerSet, environment.get("LOGEVENTS_ROOT"));
        } else {
            factory.setRootLevel(getDefaultRootLevel());
        }

        if (observerSet.isEmpty()) {
            observerSet.add(factory.getObserver("console"));
        }

        HashMap<String, LogEventObserver> globalObservers = new HashMap<>();
        configureGlobalObserversFromProperties(globalObservers, factory, properties);
        configureGlobalObserversFromEnvironment(globalObservers, factory, environment);
        observerSet.addAll(globalObservers.values());

        LoggerConfiguration logger = factory.getRootLogger();
        factory.setObserver(logger, CompositeLogEventObserver.combineList(observerSet), false);
        LogEventStatus.getInstance().addDebug(this, "Setup " + logger);
        LogEventStatus.getInstance().addConfig(this, "ROOT logger: " + logger);
    }

    protected void configureGlobalObserversFromEnvironment(Map<String, LogEventObserver> globalObservers, LogEventFactory factory, Map<String, String> environment) {
        for (Map.Entry<String, String> env : environment.entrySet()) {
            if (env.getKey().startsWith("LOGEVENTS_ROOT_OBSERVER_")) {
                String observerName = env.getKey().substring("LOGEVENTS_ROOT_OBSERVER_".length()).toLowerCase();
                addGlobalObserver(globalObservers, factory, observerName, Level.valueOf(env.getValue()));
            }
        }
    }

    protected void configureGlobalObserversFromProperties(Map<String, LogEventObserver> globalObservers, LogEventFactory factory, Properties configuration) {
        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("root.observer.")) {
                String observerName = key.toString().substring("root.observer.".length());
                Level observerThreshold = Level.valueOf(configuration.getProperty(key.toString()));
                addGlobalObserver(globalObservers, factory, observerName, observerThreshold);
            }
        }
    }

    private void addGlobalObserver(Map<String, LogEventObserver> globalObservers, LogEventFactory factory, String observerName, Level observerThreshold) {
        LogEventObserver observer = factory.getObserver(observerName);
        if (observer != null) {
            globalObservers.put(observerName, new LevelThresholdConditionalObserver(observerThreshold, observer));
        }
        LogEventStatus.getInstance().addConfig(this, "Adding root observer " + observerName);
    }

    private void configureRootLogger(LogEventFactory factory, LinkedHashSet<LogEventObserver> observerSet, String rootConfiguration) {
        String[] parts = rootConfiguration.split("\\s+", 2);
        factory.setFilter(factory.getRootLogger(), getFilter(parts[0]));

        if (parts.length > 1) {
            String observerNames = parts[1].trim();
            Stream.of(observerNames.split(",\\s*"))
                    .map(s -> factory.getObserver(s.trim()))
                    .filter(Objects::nonNull)
                    .forEach(observerSet::add);
            LogEventStatus.getInstance().addDebug(this, "Setting root observers " + observerNames);
        }
    }

    protected void configureLogger(LogEventFactory factory, LoggerConfiguration logger, String configuration, boolean includeParent) {
        String[] parts = configuration.split("\\s+", 2);
        LogEventFilter filter = getFilter(parts[0]);
        factory.setFilter(logger, filter);
        LogEventStatus.getInstance().addDebug(this, "Setting filter " + filter + " for " + logger);

        if (parts.length > 1) {
            String observerNames = parts[1].trim();
            Set<LogEventObserver> observers = Stream.of(observerNames.split(",\\s*"))
                            .map(s -> factory.getObserver(s.trim()))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            factory.setObserver(logger, CompositeLogEventObserver.combineList(observers), includeParent);
            LogEventStatus.getInstance().addDebug(this, "Set observers " + observerNames + " for " + logger);
        }
        LogEventStatus.getInstance().addConfig(this, "Logger: " + logger);
    }

    private LogEventFilter getFilter(String part) {
        if (part.contains(",")) {
            return new ConditionalLogEventFilter(part);
        } else {
            return new LevelThresholdFilter(Level.valueOf(part.trim()));
        }
    }

    private void configureObserver(Map<String, Supplier<? extends LogEventObserver>> observers, String name, String className, Properties properties) {
        if (!observers.containsKey(name)) {
            observers.put(name, () -> {
                String prefix = "observer." + name;
                try {
                    LogEventStatus.getInstance().addDebug(this, "Configuring " + prefix);
                    LogEventObserver observer = ConfigUtil.create(prefix, "org.logevents.observers", Optional.of(className), properties);
                    LogEventStatus.getInstance().addDebug(this, "Configured " + observer);
                    return observer;
                } catch (RuntimeException e) {
                    LogEventStatus.getInstance().addError(this, "Failed to create " + prefix, e);
                    throw e;
                }
            });
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + this.propertiesDir + "}";
    }

    public List<String> getConfigurationSources() {
        List<String> result = new ArrayList<>();
        for (String filename : getConfigurationFileNames()) {
            if (getClass().getClassLoader().getResourceAsStream(filename) != null) {
                result.add("classpath:" + filename);
            }
            Path path = this.propertiesDir.resolve(filename);
            if (Files.isRegularFile(path)) {
                result.add(path.toString());
            }
        }
        return result;
    }
}
