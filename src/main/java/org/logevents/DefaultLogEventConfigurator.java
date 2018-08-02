package org.logevents;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.util.ConfigUtil;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

/**
 * Default implementation of {@link LogEventConfigurator} suitable for override.
 * When overriding, {@link #configure(LogEventFactory)} should probably be overloaded
 *
 * @author Johannes Brodwall
 *
 */
public class DefaultLogEventConfigurator implements LogEventConfigurator {

    /**
     * Implementation of {@link LogEventConfigurator#configure(LogEventFactory)}.
     * Suitable for overriding.
     */
    @Override
    public void configure(LogEventFactory factory) {
        setDefaultLogging(factory);
        installJavaUtilLoggingBridge();
        loadConfigurationFiles(factory);
    }

    /**
     * Read configuration from the default configuration file based on
     * {@link #getProfiles()}
     */
    protected void loadConfigurationFiles(LogEventFactory factory) {
        configure(factory, loadConfiguration(getProfiles()));
    }

    /**
     * Used system properties and environment variables 'profiles', 'profile' and
     * 'spring.profiles.active' to determine which profiles are active in the
     * current environment.
     */
    protected List<String> getProfiles() {
        String profilesString = System.getProperty("profiles", System.getProperty("profile", System.getProperty("spring.profiles.active", "")));
        return Arrays.asList(profilesString.split(","));
    }

    /**
     * Ensures that logging to {@link java.util.logging.Logger} is intercepted.
     */
    protected void installJavaUtilLoggingBridge() {
        try {
            Class.forName("org.slf4j.bridge.SLF4JBridgeHandler");
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        } catch (ClassNotFoundException ignored) {

        }
    }

    /**
     * Logs to the console at level INFO, or level WARN if running in JUnit.
     */
    protected void setDefaultLogging(LogEventFactory factory) {
        if (isRunningInsideJunit()) {
            factory.setLevel(factory.getRootLogger(), Level.WARN);
        } else {
            factory.setLevel(factory.getRootLogger(), Level.INFO);
        }
        factory.setObserver(factory.getRootLogger(),
                new ConsoleLogEventObserver(),
                false);
    }

    /**
     * Detects whether we are currently running in a unit test. Used to set default log level.
     */
    protected boolean isRunningInsideJunit() {
        for (StackTraceElement stackTraceElement : new Throwable().getStackTrace()) {
            if (stackTraceElement.getClassName().startsWith("org.junit.runners")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads all properties files relevant for the argument set of profiles
     */
    protected Properties loadConfiguration(List<String> profiles) {
        Properties properties = new Properties();
        loadConfig(properties, "/logevents.properties");
        for (String profile : profiles) {
            if (!profile.isEmpty()) {
                loadConfig(properties, "/logevents-" + profile + ".properties");
            }
        }

        return properties;
    }

    /**
     * Inserts properties from the resource name into the properties.
     */
    protected void loadConfig(Properties properties, String resourceName) {
        try (InputStream defaultPropertiesFile = getClass().getResourceAsStream(resourceName)) {
            if (defaultPropertiesFile != null) {
                properties.load(defaultPropertiesFile);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Loads observers and loggers from the configuration. Observerers have
     * properties on the format 'observers.prefix=ClassName' and loggers have
     * properties on the format 'logger.log.name=LEVEL observer1,observer2'.
     */
    protected void configure(LogEventFactory factory, Properties configuration) {
        Map<String, LogEventObserver> observers = new HashMap<>();
        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("observer.")) {
                configureObserver(key.toString(), configuration, observers);
            }
        }

        configureLogger(factory, factory.getRootLogger(), configuration.getProperty("root"), observers);

        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("logger.")) {
                String loggerName = key.toString().substring("logger.".length());
                configureLogger(factory, factory.getLogger(loggerName), configuration.getProperty(key.toString()), observers);
            }
        }
    }

    private void configureLogger(LogEventFactory factory, LoggerConfiguration logger, String configuration, Map<String, LogEventObserver> observerMap) {
        if (configuration != null) {
            int spacePos = configuration.indexOf(' ');
            Level level = Level.valueOf(spacePos < 0 ? configuration : configuration.substring(0, spacePos).trim());
            factory.setLevel(logger, level);

            if (spacePos > 0) {
                List<LogEventObserver> observers = Stream.of(configuration.substring(spacePos+1).trim().split(","))
                        .map(getObserver(observerMap))
                        .collect(Collectors.toList());
                LogEventObserver observer = CompositeLogEventObserver.combineList(observers);
                factory.setObserver(logger, observer, true);
            }
        }
    }

    private static Function<? super String, LogEventObserver> getObserver(Map<String, LogEventObserver> observers) {
        return observerName -> observers.computeIfAbsent(observerName, k -> {
            throw new IllegalArgumentException("Unknown observer " + k + " in " + observers.keySet());
        });
    }

    private void configureObserver(String key, Properties configuration, Map<String, LogEventObserver> observers) {
        String name = key.split("\\.")[1];
        String prefix = "observer." + name;
        if (!observers.containsKey(name)) {
            observers.put(name, ConfigUtil.create(prefix, "org.logevents.observers", configuration));
        }
    }
}
