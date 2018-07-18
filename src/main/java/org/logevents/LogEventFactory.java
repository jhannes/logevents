package org.logevents;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.NullLogEventObserver;
import org.logevents.util.ConfigUtil;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public class LogEventFactory implements ILoggerFactory {

    private static LogEventFactory instance;

    public synchronized static LogEventFactory getInstance() {
        if (instance == null) {
            instance = new LogEventFactory();
        }
        return instance;
    }

    private static class RootLoggerDelegator extends LoggerDelegator {

        public RootLoggerDelegator(String name) {
            super(name);
            ownObserver = new NullLogEventObserver();
            levelThreshold = Level.WARN;
            refresh();
        }

        @Override
        void reset() {
            super.reset();
            levelThreshold = Level.WARN;
        }

        @Override
        public void refresh() {
            this.effectiveThreshold = this.levelThreshold;
            this.observer = this.ownObserver;
            refreshEventGenerators(effectiveThreshold, observer);
        }

        @Override
        public LoggerDelegator getParentLogger() {
            return null;
        }

        @Override
        public String inspect() {
            return toString() + "(effectiveThreshold=" + effectiveThreshold + ",observer=" + observer + ")";
        }

    }

    private static class CategoryLoggerDelegator extends LoggerDelegator {

        private LoggerDelegator parentLogger;

        CategoryLoggerDelegator(String name, LoggerDelegator parentLogger) {
            super(name);
            this.parentLogger = Objects.requireNonNull(parentLogger, "parentLogger" + " should not be null");
        }

        @Override
        void reset() {
            super.reset();
            this.effectiveThreshold = null;
        }

        @Override
        void refresh() {
            this.effectiveThreshold = this.levelThreshold;
            if (effectiveThreshold == null) {
                this.effectiveThreshold = parentLogger.effectiveThreshold;
            }
            observer = inheritParentObserver
                    ? CompositeLogEventObserver.combine(parentLogger.observer, ownObserver)
                    : ownObserver;

            refreshEventGenerators(effectiveThreshold, observer);
        }

        @Override
        LoggerDelegator getParentLogger() {
            return parentLogger;
        }

        @Override
        public String inspect() {
            return toString() + "(effectiveThreshold=" + effectiveThreshold + ",observer=" + observer + ") -> "
                    + (parentLogger != null ? parentLogger.inspect() : "");
        }
    }

    private LoggerDelegator rootLogger = new RootLoggerDelegator("ROOT");

    private Map<String, LoggerDelegator> loggerCache = new HashMap<String, LoggerDelegator>();

    LogEventFactory() {
        ServiceLoader<LogEventConfigurator> serviceLoader = ServiceLoader.load(LogEventConfigurator.class);
        if (!serviceLoader.iterator().hasNext()) {
            reset();
        } else {
            serviceLoader.forEach(c -> c.configure(this));
        }
    }

    @Override
    public LoggerDelegator getLogger(String name) {
        if (!loggerCache.containsKey(name)) {
            int lastPeriodPos = name.lastIndexOf('.');
            LoggerDelegator parent = lastPeriodPos < 0 ? rootLogger : getLogger(name.substring(0, lastPeriodPos));
            LoggerDelegator newLogger = new CategoryLoggerDelegator(name, parent);
            newLogger.refresh();

            loggerCache.put(name, newLogger);
        }

        return loggerCache.get(name);
    }

    public Logger getRootLogger() {
        return rootLogger;
    }

    public Level setLevel(Logger logger, Level level) {
        Level oldLevel = ((LoggerDelegator)logger).getLevelThreshold();
        ((LoggerDelegator)logger).setLevelThreshold(level);
        refreshLoggers((LoggerDelegator)logger);
        return oldLevel;
    }

    private void refreshLoggers(LoggerDelegator logger) {
        logger.refresh();
        loggerCache.values().stream().filter(l -> isParent(logger, l))
            .forEach(this::refreshLoggers);
    }

    private boolean isParent(LoggerDelegator parent, LoggerDelegator logger) {
        return logger.getParentLogger() == parent;
    }

    public LogEventObserver setObserver(Logger logger, LogEventObserver observer, boolean inheritParentObserver) {
        LogEventObserver oldObserver = ((LoggerDelegator)logger).ownObserver;
        ((LoggerDelegator)logger).setOwnObserver(observer, inheritParentObserver);
        refreshLoggers((LoggerDelegator)logger);
        return oldObserver;
    }

    public void reset() {
        rootLogger.setOwnObserver(LogEventConfiguration.consoleObserver(), false);
        loggerCache.values().forEach(logger -> logger.reset());
        refreshLoggers(rootLogger);

        configure(loadConfiguration(getProfiles()));
    }

    private List<String> getProfiles() {
        String profilesString = System.getProperty("profiles", System.getProperty("profile", System.getProperty("spring.profiles.active", "")));
        return Arrays.asList(profilesString.split(","));
    }

    private void configure(Properties configuration) {
        Map<String, LogEventObserver> observers = new HashMap<>();
        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("observer.")) {
                configureObserver(key.toString(), configuration, observers);
            }
        }

        configureLogger(getRootLogger(), configuration.getProperty("root"), observers);

        for (Object key : configuration.keySet()) {
            if (key.toString().startsWith("logger.")) {
                String loggerName = key.toString().substring("logger.".length());
                configureLogger(getLogger(loggerName), configuration.getProperty(key.toString()), observers);
            }
        }
    }

    private void configureLogger(Logger logger, String configuration, Map<String, LogEventObserver> observerMap) {
        if (configuration != null) {
            int spacePos = configuration.indexOf(' ');
            Level level = Level.valueOf(spacePos < 0 ? configuration : configuration.substring(0, spacePos).trim());
            setLevel(logger, level);

            if (spacePos > 0) {
                List<LogEventObserver> observers = Stream.of(configuration.substring(spacePos+1).trim().split(","))
                        .map(getObserver(observerMap))
                        .collect(Collectors.toList());
                LogEventObserver observer = CompositeLogEventObserver.combineList(observers);
                setObserver(logger, observer, true);
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
