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

public class DefaultLogEventsConfigurator implements LogEventConfigurator {

    @Override
    public void configure(LogEventFactory factory) {
        setDefaultLogging(factory);
        installJavaUtilLoggingBridge();
        loadConfigurationFiles(factory);
    }

    private void loadConfigurationFiles(LogEventFactory factory) {
        configure(factory, loadConfiguration(getProfiles()));
    }

    private List<String> getProfiles() {
        String profilesString = System.getProperty("profiles", System.getProperty("profile", System.getProperty("spring.profiles.active", "")));
        return Arrays.asList(profilesString.split(","));
    }

    private void installJavaUtilLoggingBridge() {
        try {
            Class.forName("org.slf4j.bridge.SLF4JBridgeHandler");
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        } catch (ClassNotFoundException ignored) {

        }
    }

    private void setDefaultLogging(LogEventFactory factory) {
        factory.setLevel(factory.getRootLogger(), Level.INFO);
        factory.setObserver(factory.getRootLogger(),
                new ConsoleLogEventObserver(),
                false);
    }

    private void configure(LogEventFactory factory, Properties configuration) {
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

    private Properties loadConfiguration(List<String> profiles) {
        Properties properties = new Properties();
        loadConfig(properties, "/logevents.properties");
        for (String profile : profiles) {
            if (!profile.isEmpty()) {
                loadConfig(properties, "/logevents-" + profile + ".properties");
            }
        }

        return properties;
    }

    private void loadConfig(Properties properties, String filename) {
        try (InputStream defaultPropertiesFile = getClass().getResourceAsStream(filename)) {
            if (defaultPropertiesFile != null) {
                properties.load(defaultPropertiesFile);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
