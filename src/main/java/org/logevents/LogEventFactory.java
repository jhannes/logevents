package org.logevents;

import java.util.HashMap;
import java.util.Map;

import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.NullLogEventObserver;
import org.logevents.util.Validate;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public class LogEventFactory implements ILoggerFactory {

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
            this.parentLogger = Validate.notNull(parentLogger, "parentLogger");
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

    public LogEventFactory() {
        reset();
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
        rootLogger.setOwnObserver(LogEventConfigurator.consoleObserver(), false);
        loggerCache.values().forEach(logger -> logger.reset());
        refreshLoggers(rootLogger);
    }

}
