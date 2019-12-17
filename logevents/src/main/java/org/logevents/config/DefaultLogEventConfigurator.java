package org.logevents.config;

import org.logevents.LogEventConfigurator;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LoggerConfiguration;
import org.logevents.observers.CompositeLogEventObserver;
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
 * By default, observers named <code>observer.console</code> {@link ConsoleLogEventObserver} and
 * <code>observer.file</code> {@link FileLogEventObserver} are created. By default, the <code>root</code>
 * root logger is set to <code>INFO console</code>.
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
    private Path propertiesDir;
    private Thread configurationWatcher;

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
            factory.reset(new ConsoleLogEventObserver(), getDefaultRootLevel());
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
        return loadPropertiesFromFiles(getConfigurationFileNames());
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
        return profiles;
    }

    /**
     * Loads all properties files relevant for the argument set of profiles
     * from classpath and current working directory.
     *
     * @param configurationFileNames the file names to load
     * @return Properties with the configuration of all files merged together
     */
    protected Properties loadPropertiesFromFiles(List<String> configurationFileNames) {
        LogEventStatus.getInstance().addConfig(this, "Loading configuration from " + configurationFileNames);
        Properties properties = new Properties();
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
     * @param configuration The merged configuration that should be applied to the factory
     */
    public void applyConfigurationProperties(LogEventFactory factory, Properties configuration) {
        Configuration logeventsConfig = new Configuration(configuration, "logevents");
        LogEventStatus.getInstance().configure(logeventsConfig);
        showWelcomeMessage(configuration);
        Map<String, Supplier<? extends LogEventObserver>> observers = new HashMap<>();
        for (Object key : configuration.keySet()) {
            if (key.toString().matches("observer\\.\\w+")) {
                configureObserver(observers, key.toString(), configuration);
            }
        }
        observers.putIfAbsent("console", () -> createConsoleLogEventObserver(configuration));
        observers.putIfAbsent("file", () -> createFileObserver(configuration));
        factory.setObservers(observers);
        factory.reset("console", getDefaultRootLevel());
        configureRootLogger(factory, configuration);

        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("logger.")) {
                String loggerName = key.toString().substring("logger.".length());
                boolean includeParent = !"false".equalsIgnoreCase(configuration.getProperty("includeParent." + loggerName));
                String logger = configuration.getProperty(key.toString());
                configureLogger(factory, factory.getLogger(loggerName), logger, includeParent);
            }
        }
        if (logeventsConfig.getBoolean("installExceptionHandler")) {
            if (Thread.getDefaultUncaughtExceptionHandler() == null) {
                Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
                    factory.getRootLogger().error("Thread {} terminated with unhandled exception", thread.getName(), e);
                });
                LogEventStatus.getInstance().addConfig(this, "Installing default uncaught exception handler");
            } else {
                LogEventStatus.getInstance().addDebug(this, "Uncaught exception handler already set to " + Thread.getDefaultUncaughtExceptionHandler());
            }
        }
        logeventsConfig.checkForUnknownFields();
    }

    protected void showWelcomeMessage(Properties configuration) {
        if (configuration.isEmpty()) {
            LogEventStatus.getInstance().addInfo(this, WELCOME_MESSAGE);
        }
    }

    protected FileLogEventObserver createFileObserver(Properties configuration) {
        LogEventStatus.getInstance().addDebug(this, "Configuring observer.console");
        FileLogEventObserver observer = new FileLogEventObserver(configuration, "observer.file");
        LogEventStatus.getInstance().addDebug(this, "Configured " + observer);
        return observer;
    }

    protected ConsoleLogEventObserver createConsoleLogEventObserver(Properties configuration) {
        LogEventStatus.getInstance().addDebug(this, "Configuring observer.console");
        ConsoleLogEventObserver observer = new ConsoleLogEventObserver(configuration, "observer.console");
        LogEventStatus.getInstance().addDebug(this, "Configured " + observer);
        return observer;
    }

    private void configureRootLogger(LogEventFactory factory, Properties configuration) {
        LinkedHashSet<LogEventObserver> observerSet = new LinkedHashSet<>();
        Level level = null;

        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("root.observer.")) {
                String observerName = key.toString().substring("root.observer.".length());
                LogEventObserver observer = factory.getObserver(observerName);
                Level observerThreshold = Level.valueOf(configuration.getProperty(key.toString()));
                if (observer != null) {
                    observerSet.add(new LevelThresholdConditionalObserver(observerThreshold, observer));
                    if (level == null || observerThreshold.toInt() < level.toInt()) {
                        level = observerThreshold;
                    }
                }
                LogEventStatus.getInstance().addDebug(this, "Adding root observer " + observerName);
            }
        }

        String rootConfiguration = configuration.getProperty("root");
        if (rootConfiguration != null) {
            int spacePos = rootConfiguration.indexOf(' ');
            Level rootLevel = Level.valueOf(spacePos < 0 ? rootConfiguration : rootConfiguration.substring(0, spacePos).trim());
            if (level == null || rootLevel.toInt() < level.toInt()) {
                level = rootLevel;
            }
            Level actualLevel = level;

            if (spacePos > 0) {
                String observerNames = rootConfiguration.substring(spacePos + 1).trim();
                Stream.of(observerNames.split(",\\s*"))
                        .map(s -> factory.getObserver(s.trim()))
                        .filter(Objects::nonNull)
                        .map(o -> rootLevel != actualLevel ? new LevelThresholdConditionalObserver(rootLevel, o) : o)
                        .forEach(observerSet::add);
                LogEventStatus.getInstance().addDebug(this, "Setting root observers " + observerNames);
            }
        }

        LoggerConfiguration logger = factory.getRootLogger();
        LogEventStatus.getInstance().addDebug(this, "Setting level " + level + " for " + logger);
        if (level != null) {
            factory.setLevel(logger, level);
        }
        factory.setObserver(logger, CompositeLogEventObserver.combineList(observerSet), false);
    }

    private void configureLogger(LogEventFactory factory, LoggerConfiguration logger, String configuration, boolean includeParent) {
        if (configuration != null) {
            int spacePos = configuration.indexOf(' ');
            Level level = Level.valueOf(spacePos < 0 ? configuration : configuration.substring(0, spacePos).trim());
            factory.setLevel(logger, level);
            LogEventStatus.getInstance().addDebug(this, "Setting level " + level + " for " + logger);

            if (spacePos > 0) {
                String observerNames = configuration.substring(spacePos + 1).trim();
                Set<LogEventObserver> observers1 = Stream.of(observerNames.split(",\\s*"))
                                .map(s -> factory.getObserver(s.trim()))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                factory.setObserver(logger, CompositeLogEventObserver.combineList(observers1), includeParent);
                LogEventStatus.getInstance().addDebug(this, "Setting observers " + observerNames + " for " + logger);
            }
        }
    }

    private void configureObserver(Map<String, Supplier<? extends LogEventObserver>> observers, String key, Properties configuration) {
        String name = key.split("\\.")[1];
        if (!observers.containsKey(name)) {
            observers.put(name, () -> {
                String prefix = "observer." + name;
                try {
                    LogEventStatus.getInstance().addDebug(this, "Configuring " + prefix);
                    LogEventObserver observer = ConfigUtil.create(prefix, "org.logevents.observers", configuration);
                    LogEventStatus.getInstance().addDebug(this, "Configured " + observer);
                    return observer;
                } catch (RuntimeException e) {
                    LogEventStatus.getInstance().addError(this, "Failed to create " + key, e);
                    throw e;
                }
            });
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + this.propertiesDir + "}";
    }
}
