package org.logevents;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.NullLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

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
        }
        return instance;
    }

    static class RootLoggerDelegator extends LoggerDelegator {

        public RootLoggerDelegator() {
            super("ROOT");
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
    }

    private LoggerDelegator rootLogger = new RootLoggerDelegator();

    private Map<String, LoggerDelegator> loggerCache = new HashMap<String, LoggerDelegator>();

    LogEventFactory() {
        configure();
    }

    @Override
    public synchronized LoggerConfiguration getLogger(String name) {
        if (!loggerCache.containsKey(name)) {
            int lastPeriodPos = name.lastIndexOf('.');
            LoggerConfiguration parent = (lastPeriodPos < 0 ? rootLogger : getLogger(name.substring(0, lastPeriodPos)));
            LoggerDelegator newLogger = new CategoryLoggerDelegator(name, (LoggerDelegator) parent);
            newLogger.refresh();

            loggerCache.put(name, newLogger);
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
        loggerCache.values().stream().filter(l -> isParent(logger, l))
            .forEach(this::refreshLoggers);
    }

    private boolean isParent(LoggerDelegator parent, LoggerDelegator logger) {
        return logger.getParentLogger() == parent;
    }

    public void setRootObserver(LogEventObserver observer) {
        setObserver(getRootLogger(), observer, true);
    }

    public void setObserver(String loggerName, LogEventObserver observer) {
        setObserver(getLogger(loggerName), observer, true);
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
        LogEventObserver oldObserver = ((LoggerDelegator)logger).ownObserver;
        ((LoggerDelegator)logger).setOwnObserver(observer, inheritParentObserver);
        refreshLoggers((LoggerDelegator)logger);
        return oldObserver;
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
        LogEventObserver oldObserver = ((LoggerDelegator)logger).ownObserver;
        ((LoggerDelegator)logger).setOwnObserver(
                CompositeLogEventObserver.combine(observer, oldObserver),
                ((LoggerDelegator)logger).inheritParentObserver);
        refreshLoggers((LoggerDelegator)logger);
    }

    /**
     * Reads logging configuration from {@link LogEventConfigurator} configured
     * with {@link ServiceLoader}. If none exists, uses {@link DefaultLogEventConfigurator}.
     * This method is called the first time {@link #getInstance()} is called.
     */
    public void configure() {
        rootLogger.setOwnObserver(new ConsoleLogEventObserver(), false);
        loggerCache.values().forEach(logger -> logger.reset());
        ServiceLoader<LogEventConfigurator> serviceLoader = ServiceLoader.load(LogEventConfigurator.class);

        if (!serviceLoader.iterator().hasNext()) {
            LogEventStatus.getInstance().addInfo(this, "No configuration found - using default");
            if (isRunningInsideJunit()) {
                new DefaultTestLogEventConfigurator().configure(this);
            } else {
                new DefaultLogEventConfigurator().configure(this);
            }
        } else {
            serviceLoader.forEach(c -> {
                LogEventStatus.getInstance().addInfo(this, "Loading service loader " + c);
                c.configure(this);
            });
        }
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
}
