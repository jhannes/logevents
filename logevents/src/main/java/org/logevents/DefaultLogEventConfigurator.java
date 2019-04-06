package org.logevents;

import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.FileLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ConfigUtil;
import org.logevents.util.LogEventConfigurationException;
import org.slf4j.event.Level;

import java.io.FileInputStream;
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
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Default implementation of {@link LogEventConfigurator} which reads the
 * logging configuration from <code>logevents.properties</code>-files.
 * This class will by default load <code>logevents-<em>profile</em>.properties</code>
 * and <code>logevents.properties</code>, where based on {@link #getProfiles()}.
 * <code>logevents.properties</code> files in profile takes precedence over default
 * <code>logevents.properties</code> files. Files on current working directory takes
 * precedence over files in classpath and are automatically scanned for changes.
 * <p>
 * <code>logevents.properties</code> should look like this ({@link #applyConfigurationProperties}):
 *
 * <pre>
 * root=LEVEL [observer1,observer2,observer2]
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

    private Path propertiesDir;
    private WatchService newWatchService;
    private Map<String, LogEventObserver> observers = new HashMap<>();

    public DefaultLogEventConfigurator(Path propertiesDir) {
        this.propertiesDir = propertiesDir;
    }

    public DefaultLogEventConfigurator() {
        this(Paths.get(".").toAbsolutePath().normalize());
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
            newWatchService = propertiesDir.getFileSystem().newWatchService();
            propertiesDir.register(newWatchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

            Thread configurationWatcher = new Thread(() -> {
                try {
                    while (true) {
                        WatchKey key = newWatchService.take();
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
                            resetConfigurationFromFiles(factory);
                        }
                    }
                } catch (InterruptedException e) {
                    LogEventStatus.getInstance().addInfo(DefaultLogEventConfigurator.this,
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
            reset(factory);
        }
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
        String profilesString = System.getProperty("profiles", System.getProperty("spring.profiles.active", ""));
        return Arrays.asList(profilesString.split(","));
    }

    /**
     * Loads all properties files relevant for the argument set of profiles
     * from classpath and current working directory.
     *
     * @param configurationFileNames the file names to load
     * @return Properties with the configuration of all files merged together
     */
    protected Properties loadPropertiesFromFiles(List<String> configurationFileNames) {
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
                properties.load(propertiesFile);
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
        observers.clear();
        for (Object key : configuration.keySet()) {
            if (key.toString().matches("observer\\.\\w+")) {
                configureObserver(key.toString(), configuration);
            }
        }
        observers.putIfAbsent("console", createConsoleLogEventObserver(configuration));
        observers.putIfAbsent("file", new FileLogEventObserver(configuration, "observer.file"));

        reset(factory);
        configureLogger(factory, factory.getRootLogger(), configuration.getProperty("root"), false);

        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("logger.")) {
                String loggerName = key.toString().substring("logger.".length());
                boolean includeParent = !"false".equalsIgnoreCase(configuration.getProperty("includeParent." + loggerName));
                configureLogger(factory, factory.getLogger(loggerName),
                        configuration.getProperty(key.toString()),
                        includeParent);
            }
        }
    }

    protected ConsoleLogEventObserver createConsoleLogEventObserver(Properties configuration) {
        return new ConsoleLogEventObserver(configuration, "observer.console");
    }

    protected void reset(LogEventFactory factory) {
        factory.reset(getObserver("console"));
    }

    private void configureLogger(LogEventFactory factory, LoggerConfiguration logger, String configuration, boolean includeParent) {
        if (configuration != null) {
            int spacePos = configuration.indexOf(' ');
            Level level = Level.valueOf(spacePos < 0 ? configuration : configuration.substring(0, spacePos).trim());
            factory.setLevel(logger, level);

            if (spacePos > 0) {
                Set<LogEventObserver> observers =
                        Stream.of(configuration.substring(spacePos+1).trim().split(","))
                        .map(s -> getObserver(s.trim()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                LogEventObserver observer = CompositeLogEventObserver.combineList(observers);
                factory.setObserver(logger, observer, includeParent);
            }
        }
    }

    LogEventObserver getObserver(String observerName) {
        return observers.computeIfAbsent(observerName, key -> {
            throw new LogEventConfigurationException("Unknown observer <" + key + ">");
        });
    }

    private void configureObserver(String key, Properties configuration) {
        try {
            String name = key.split("\\.")[1];
            String prefix = "observer." + name;
            if (!observers.containsKey(name)) {
                observers.put(name, ConfigUtil.create(prefix, "org.logevents.observers", configuration));
            }
        } catch (RuntimeException e) {
            LogEventStatus.getInstance().addError(this, "Failed to create " + key, e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + this.propertiesDir + "}";
    }
}
