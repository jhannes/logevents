package org.logevents;

import org.logevents.config.DefaultLogEventConfigurator;
import org.logevents.config.DefaultTestLogEventConfigurator;
import org.logevents.impl.JavaUtilLoggingAdapter;
import org.logevents.impl.LoggerDelegator;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.util.LogEventConfigurationException;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LogEventFactory holds all active loggers and lets you set the
 * level and observers for these. It implements {@link ILoggerFactory}
 * and is the entry point of Log Events as an SLF4J implementation.
 * <p>
 * At startup time, this class will use a {@link LogEventConfigurator}
 * specified with {@link ServiceLoader} to set up the log configuration.
 * If none is available, {@link DefaultLogEventConfigurator} will be used.
 * <p>
 * Use {@link #setRootLevel(Level)} and {@link #setLevel(Logger, Level)}
 * to configure the threshold for logging at one level. If null is specified,
 * the parent log chain will be searched for a log level threshold.
 * <p>
 * Use {@link #addObserver}, {@link #addRootObserver}, {@link #setObserver}
 * and {@link #setRootObserver} to configure the observers for logging.
 * Observers are inherited from parents, unless inherit is set to false
 * for {@link #setObserver(Logger, LogEventObserver, boolean)}.
 *
 * @author Johannes Brodwall
 *
 */
public class LogEventFactory implements ILoggerFactory {

    private static LogEventFactory instance;
    private Map<String, LogEventObserver> observers = new HashMap<>();

    /**
     * Retrieve the singleton instance for the JVM.
     */
    public synchronized static LogEventFactory getInstance() {
        if (instance == null) {
            instance = new LogEventFactory();
            JavaUtilLoggingAdapter.install(instance);
        }
        return instance;
    }

    public void setObservers(Map<String, LogEventObserver> observers) {
        this.observers = observers;
    }

    private LoggerDelegator rootLogger = LoggerDelegator.rootLogger();

    private Map<String, LoggerDelegator> loggerCache = new HashMap<>();

    public LogEventFactory() {
        configure();
    }

    @Override
    public synchronized LoggerConfiguration getLogger(String name) {
        if (!loggerCache.containsKey(name)) {
            int lastPeriodPos = name.lastIndexOf('.');
            LoggerDelegator parent = (LoggerDelegator)(lastPeriodPos < 0 ? rootLogger : getLogger(name.substring(0, lastPeriodPos)));
            loggerCache.put(name, parent.getChildLogger(name));
        }

        return loggerCache.get(name);
    }

    /**
     * Used to report the log configuration.
     */
    public Map<String, LoggerConfiguration> getLoggers() {
        return Collections.unmodifiableMap(loggerCache);
    }

    public LoggerConfiguration getRootLogger() {
        return rootLogger;
    }

    public void setRootLevel(Level level) {
        setLevel(getRootLogger(), level);
    }

    /**
     * Sets the threshold to log at. All messages lower than the threshold will be ignored.
     *
     * @param loggerName The non-nullable name of the logger as per {@link #getLogger}
     * @param level The nullable name of the threshold. If null, inherit from parent
     */
    public void setLevel(String loggerName, Level level) {
        setLevel(getLogger(loggerName), level);
    }

    /**
     * Sets the threshold to log at. All messages lower than the threshold will be ignored.
     *
     * @param logger The non-nullable logger
     * @param level The nullable name of the threshold. If null, inherit from parent
     */
    public Level setLevel(Logger logger, Level level) {
        Level oldLevel = ((LoggerDelegator)logger).getLevelThreshold();
        ((LoggerDelegator)logger).setLevelThreshold(level);
        refreshLoggers((LoggerDelegator)logger);
        return oldLevel;
    }

    private void refreshLoggers(LoggerDelegator logger) {
        logger.refresh();
        loggerCache.values().stream().filter(l -> l.hasParent(logger)).forEach(this::refreshLoggers);
    }

    public void setRootObserver(LogEventObserver observer) {
        setObserver(getRootLogger(), observer, true);
    }

    public void setObserver(String loggerName, LogEventObserver observer) {
        LoggerConfiguration logger = getLogger(loggerName);
        logger.replaceObserver(observer);
        refreshLoggers((LoggerDelegator) logger);
    }

    public LogEventObserver getObserver(String observerName) {
        return observers.computeIfAbsent(observerName, key -> {
            throw new LogEventConfigurationException("Unknown observer <" + key + ">");
        });
    }

    /**
     * Sets the observer that should be used to receive LogEvents for this logger
     * and children.
     *
     * @param logger The logger to set
     * @param observer The nullable observer. Use {@link CompositeLogEventObserver} to register more than one observer
     * @param inheritParentObserver If true, observers set on the observers will also receive log events
     * @return The previous observer. Useful if you want to temporarily set the observer
     */
    public LogEventObserver setObserver(Logger logger, LogEventObserver observer, boolean inheritParentObserver) {
        LogEventObserver oldObserver = ((LoggerDelegator)logger).setObserver(observer, inheritParentObserver);
        refreshLoggers((LoggerDelegator)logger);
        return oldObserver;
    }

    public void setObserverNames(LoggerConfiguration logger, String observerNames, boolean includeParent) {
        Set<LogEventObserver> observers =
                Stream.of(observerNames.split(","))
                        .map(s -> getObserver(s.trim()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        LogEventObserver observer = CompositeLogEventObserver.combineList(observers);
        setObserver(logger, observer, includeParent);
    }

    /**
     * Adds a new nullable observer to the current observers for the root logger
     */
    public void addRootObserver(LogEventObserver observer) {
        addObserver(getRootLogger(), observer);
    }

    /**
     * Adds a new observer to the current observers for the specified logger
     */
    public void addObserver(String loggerName, LogEventObserver observer) {
        addObserver(getLogger(loggerName), observer);
    }

    /**
     * Adds a new nullable observer to the current observers for the specified logger
     */
    private void addObserver(Logger logger, LogEventObserver observer) {
        ((LoggerDelegator)logger).addObserver(observer);
        refreshLoggers((LoggerDelegator)logger);
    }

    /**
     * Reads logging configuration from {@link LogEventConfigurator} configured
     * with {@link ServiceLoader}. If none exists, uses {@link DefaultLogEventConfigurator}.
     * This method is called the first time {@link #getInstance()} is called.
     */
    public void configure() {
        reset(new ConsoleLogEventObserver(), Level.INFO);
        for (LogEventConfigurator configurator : getConfigurators()) {
            LogEventStatus.getInstance().addInfo(this, "Loading service loader " + configurator);
            configurator.configure(this);
        }
    }

    public List<LogEventConfigurator> getConfigurators() {
        List<LogEventConfigurator> configurators = new ArrayList<>();
        ServiceLoader.load(LogEventConfigurator.class).forEach(configurators::add);
        if (configurators.isEmpty()) {
            LogEventStatus.getInstance().addDebug(this, "No configuration found - using default");
            if (isRunningInsideJunit()) {
                configurators.add(new DefaultTestLogEventConfigurator());
            } else {
                configurators.add(new DefaultLogEventConfigurator());
            }
        }
        return configurators;
    }

    /**
     * Logs to the console at level INFO, or level WARN if running in JUnit.
     * @param rootObserver The observer used for {@see getRootLogger}
     * @param rootLevel The logging threshold for {@see getRootLogger}
     */
    public void reset(LogEventObserver rootObserver, Level rootLevel) {
        rootLogger.reset();
        loggerCache.values().forEach(LoggerDelegator::reset);
        rootLogger.setOwnObserver(rootObserver, false);
        rootLogger.setLevelThreshold(rootLevel);
        refreshLoggers(rootLogger);
    }

    public void reset(String rootObserverName, Level rootLevel) {
        reset(getObserver(rootObserverName), rootLevel);
    }

    /**
     * Detects whether we are currently running in a unit test. Used to set default log level.
     */
    protected boolean isRunningInsideJunit() {
        for (StackTraceElement stackTraceElement : new Throwable().getStackTrace()) {
            if (stackTraceElement.getClassName().matches("^org.junit.(runners|platform.engine).*")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{}";
    }
}
