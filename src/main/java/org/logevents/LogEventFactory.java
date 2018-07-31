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
        configure();
    }

    @Override
    public LoggerConfiguration getLogger(String name) {
        if (!loggerCache.containsKey(name)) {
            int lastPeriodPos = name.lastIndexOf('.');
            LoggerConfiguration parent = (lastPeriodPos < 0 ? rootLogger : getLogger(name.substring(0, lastPeriodPos)));
            LoggerDelegator newLogger = new CategoryLoggerDelegator(name, (LoggerDelegator) parent);
            newLogger.refresh();

            loggerCache.put(name, newLogger);
        }

        return loggerCache.get(name);
    }

    public Map<String, LoggerConfiguration> getLoggers() {
        return Collections.unmodifiableMap(loggerCache);
    }

    public LoggerConfiguration getRootLogger() {
        return rootLogger;
    }

    public void setLevel(Level level) {
        setLevel(getRootLogger(), level);
    }

    public void setLevel(String loggerName, Level level) {
        setLevel(getLogger(loggerName), level);
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

    public void setObserver(LogEventObserver observer) {
        setObserver(getRootLogger(), observer, true);
    }

    public void setObserver(String loggerName, LogEventObserver observer) {
        setObserver(getLogger(loggerName), observer, true);
    }

    public LogEventObserver setObserver(Logger logger, LogEventObserver observer, boolean inheritParentObserver) {
        LogEventObserver oldObserver = ((LoggerDelegator)logger).ownObserver;
        ((LoggerDelegator)logger).setOwnObserver(observer, inheritParentObserver);
        refreshLoggers((LoggerDelegator)logger);
        return oldObserver;
    }

    public void addObserver(LogEventObserver observer) {
        addObserver(getRootLogger(), observer);
    }

    public void addObserver(String loggerName, LogEventObserver observer) {
        addObserver(getLogger(loggerName), observer);
    }

    public void addObserver(Logger logger, LogEventObserver observer) {
        LogEventObserver oldObserver = ((LoggerDelegator)logger).ownObserver;
        ((LoggerDelegator)logger).setOwnObserver(
                CompositeLogEventObserver.combine(observer, oldObserver),
                ((LoggerDelegator)logger).inheritParentObserver);
        refreshLoggers((LoggerDelegator)logger);
    }


    public void configure() {
        rootLogger.setOwnObserver(new ConsoleLogEventObserver(), false);
        loggerCache.values().forEach(logger -> logger.reset());
        ServiceLoader<LogEventConfigurator> serviceLoader = ServiceLoader.load(LogEventConfigurator.class);

        if (!serviceLoader.iterator().hasNext()) {
            LogEventStatus.getInstance().addInfo(this, "No configuration found - using default");
            new DefaultLogEventConfigurator().configure(this);
        } else {
            serviceLoader.forEach(c -> {
                LogEventStatus.getInstance().addInfo(this, "Loading service loader " + c);
                c.configure(this);
            });
        }
    }


}
