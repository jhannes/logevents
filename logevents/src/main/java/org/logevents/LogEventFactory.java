package org.logevents;

import org.logevents.config.DefaultLogEventConfigurator;
import org.logevents.config.DefaultTestLogEventConfigurator;
import org.logevents.config.LogEventConfigurationException;
import org.logevents.core.CompositeLogEventObserver;
import org.logevents.core.JavaUtilLoggingAdapter;
import org.logevents.core.LogEventFilter;
import org.logevents.core.LoggerDelegator;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    /**
     * Retrieve the singleton instance for the JVM.
     */
    public synchronized static LogEventFactory getInstance() {
        if (instance == null) {
            instance = new LogEventFactory();
            instance.configure();
        }
        return instance;
    }

    private final Map<String, Supplier<? extends LogEventObserver>> observerSuppliers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, LogEventObserver> observers = new HashMap<>();

    private final LoggerDelegator rootLogger = LoggerDelegator.rootLogger();
    private final Map<String, LoggerDelegator> loggerCache = new TreeMap<>();
    private final Map<String, LoggerDelegator> loggerAliasCache = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    @Override
    public synchronized LogEventLogger getLogger(String name) {
        if (name == null || name.equals(Logger.ROOT_LOGGER_NAME)) {
            return rootLogger;
        }
        if (loggerCache.containsKey(name)) {
            return loggerCache.get(name);
        }

        if (loggerAliasCache.containsKey(name)) {
            LoggerDelegator logger = loggerAliasCache.get(name).withName(name);
            loggerCache.put(name, logger);
            return logger;
        } else {
            int lastPeriodPos = name.lastIndexOf('.');
            LoggerDelegator parent = (LoggerDelegator)(lastPeriodPos < 0 ? rootLogger : getLogger(name.substring(0, lastPeriodPos)));
            LoggerDelegator logger = parent.getChildLogger(name);
            loggerCache.put(name, logger);
            loggerAliasCache.put(name, logger);
            return logger;
        }
    }

    /**
     * Used to report the log configuration.
     */
    public Map<String, LogEventLogger> getLoggers() {
        return Collections.unmodifiableMap(loggerCache);
    }

    public LogEventLogger getRootLogger() {
        return rootLogger;
    }

    public void setRootLevel(Level level) {
        setLevel(getRootLogger(), level);
    }

    /**
     * Sets the threshold to log at. All messages lower than the threshold will be ignored.
     *
     * @param logger The non-nullable logger
     * @param level The nullable name of the threshold. If null, inherit from parent
     */
    public LogEventFilter setLevel(Logger logger, Level level) {
        return setFilter(logger, level != null ? new LogEventFilter(level) : null);
    }

    /**
     * Sets the threshold to log at. All messages lower than the threshold will be ignored.
     *
     * @param loggerName The non-nullable logger
     * @param level The nullable name of the threshold. If null, inherit from parent
     */
    public LogEventFilter setLevel(String loggerName, Level level) {
        return setLevel(getLogger(loggerName), level);
    }

    /**
     * See {@link #setFilter(Logger, LogEventFilter)}
     */
    public LogEventFilter setFilter(String loggerName, LogEventFilter filter) {
       return setFilter(getLogger(loggerName), filter);
    }

    /**
     * Sets a filter where only log events that fulfil the criteria will be
     * forwarded to observers. LogEvents optimizes filtering at configuration
     * time so evaluation is as quick as possible
     */
    public LogEventFilter setFilter(Logger logger, LogEventFilter filter) {
        LogEventFilter oldFilter = ((LoggerDelegator)logger).getOwnFilter();
        ((LoggerDelegator)logger).setFilter(filter);
        refreshLoggers((LoggerDelegator)logger);
        aliasLoggers(logger).forEach(alias -> {
            alias.setFilter(filter);
            refreshLoggers(alias);
        });
        LogEventStatus.getInstance().addDebug(this, "setFilter: " + logger);
        return oldFilter;
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
        aliasLoggers(logger).forEach(alias -> {
            alias.setObserver(observer, inheritParentObserver);
            refreshLoggers(alias);
        });
        LogEventStatus.getInstance().addDebug(this, "setObserver " + logger);
        return oldObserver;
    }

    public LogEventObserver setRootObserver(LogEventObserver observer) {
        return setObserver(getRootLogger(), observer, true);
    }

    public void setObserver(String loggerName, LogEventObserver observer) {
        setObserver(getLogger(loggerName), observer);
    }

    public LogEventObserver setObserver(LogEventLogger logger, LogEventObserver observer) {
        LogEventObserver oldObserver = logger.replaceObserver(observer);
        refreshLoggers((LoggerDelegator) logger);
        aliasLoggers(logger).forEach(alias -> {
            alias.replaceObserver(observer);
            refreshLoggers(alias);
        });
        LogEventStatus.getInstance().addDebug(this, "setObserver " + logger);
        return oldObserver;
    }

    private void refreshLoggers(LoggerDelegator logger) {
        logger.refresh();
        JavaUtilLoggingAdapter.installHandler(this, logger);
        new ArrayList<>(loggerCache.values()).stream().filter(l -> l.hasParent(logger))
                .collect(Collectors.toList())
                .forEach(this::refreshLoggers);
    }

    private List<LoggerDelegator> aliasLoggers(Logger logger) {
        return loggerCache.values().stream()
                .filter(l -> l != logger)
                .filter(l -> l.getName().equalsIgnoreCase(logger.getName()))
                .collect(Collectors.toList());
    }

    public Collection<String> getObserverNames() {
        return observerSuppliers.keySet();
    }

    public LogEventObserver getObserver(String observerName) {
        try {
            return tryGetObserver(observerName);
        } catch (RuntimeException e) {
            LogEventStatus.getInstance().addFatal(this, "Failed to load " + observerName, e);
            return null;
        }
    }

    public LogEventObserver tryGetObserver(String observerName) {
        if (observers.containsKey(observerName)) {
            return observers.get(observerName);
        }
        if (!observerSuppliers.containsKey(observerName)) {
            throw new LogEventConfigurationException("Unknown observer <" + observerName + ">");
        }
        observers.put(observerName, observerSuppliers.get(observerName).get());
        return observers.get(observerName);
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
    public void addObserver(Logger logger, LogEventObserver observer) {
        ((LoggerDelegator)logger).addObserver(observer);
        refreshLoggers((LoggerDelegator)logger);
        aliasLoggers(logger).forEach(alias -> {
            alias.addObserver(observer);
            refreshLoggers(alias);
        });
        LogEventStatus.getInstance().addDebug(this, "addObserver " + logger);
    }

    /**
     * Reads logging configuration from {@link LogEventConfigurator} configured
     * with {@link ServiceLoader}. If none exists, uses {@link DefaultLogEventConfigurator}.
     * This method is called the first time {@link #getInstance()} is called.
     */
    public synchronized void configure() {
        setObservers(new HashMap<>());
        setRootLevel(Level.INFO);
        setRootObserver(new ConsoleLogEventObserver());
        for (LogEventConfigurator configurator : getConfigurators()) {
            LogEventStatus.getInstance().addConfig(this, "Loading service loader " + configurator);
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

    public void setObservers(Map<String, Supplier<? extends LogEventObserver>> observerSuppliers) {
        shutdownObservers();
        this.observers.clear();
        this.observerSuppliers.clear();
        this.observerSuppliers.putAll(observerSuppliers);
        rootLogger.reset();
        loggerCache.values().forEach(LoggerDelegator::reset);
        refreshLoggers(rootLogger);
    }

    /**
     * Detects whether we are currently running in a unit test. Used to set default log level.
     */
    public boolean isRunningInsideJunit() {
        for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
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

    public void shutdownObservers() {
        for (LogEventObserver value : observers.values()) {
            if (value instanceof AutoCloseable) {
                try {
                    ((AutoCloseable)value).close();
                } catch (Exception e) {
                    LogEventStatus.getInstance().addError(this, "Failed to shut down " + value, e);
                }
            }
        }
    }

    public boolean isObserverCreated(String observerName) {
        return observers.containsKey(observerName);
    }
}
