package org.logevents.impl;

import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LoggerConfiguration;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.LevelThresholdFilter;
import org.logevents.observers.LogEventFilter;
import org.logevents.observers.NullLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Internal implementation of SLF4J {@link Logger}. Uses
 * {@link #ownFilter}, {@link #ownObserver} and {@link #inheritParentObserver}
 * as configuration state and calculates {@link #observer} and {@link #effectiveFilter}
 * based on these. Uses {@link LogEventGenerator} internal class as strategy
 * pattern to either ignore everything at a given level or create a {@link LogEvent}.
 *
 * @author Johannes Brodwall
 *
 */
@SuppressWarnings("JavaDoc")
public abstract class LoggerDelegator implements LoggerConfiguration {

    private static class RootLoggerDelegator extends LoggerDelegator {

        RootLoggerDelegator() {
            super("ROOT");
            ownObserver = new NullLogEventObserver();
            ownFilter = new LevelThresholdFilter(Level.INFO);
            refresh();
        }

        @Override
        public void reset() {
            super.reset();
            ownFilter = new LevelThresholdFilter(Level.INFO);
        }

        @Override
        public void refresh() {
            this.effectiveFilter = this.ownFilter;
            this.observer = this.ownObserver;
            refreshEventGenerators();
        }

        @Override
        public boolean hasParent(LoggerDelegator parent) {
            return false;
        }
    }

    private static class CategoryLoggerDelegator extends LoggerDelegator {

        private LoggerDelegator parentLogger;

        CategoryLoggerDelegator(String name, LoggerDelegator parentLogger) {
            super(name);
            this.parentLogger = Objects.requireNonNull(parentLogger, "parentLogger" + " should not be null");
            refresh();
        }

        @Override
        public void reset() {
            super.reset();
            this.effectiveFilter = null;
        }

        @Override
        public void refresh() {
            this.effectiveFilter = this.ownFilter;
            if (effectiveFilter == null) {
                this.effectiveFilter = parentLogger.effectiveFilter;
            }
            observer = inheritParentObserver
                    ? CompositeLogEventObserver.combine(parentLogger.observer, ownObserver)
                    : ownObserver;

            refreshEventGenerators();
        }

        @Override
        public boolean hasParent(LoggerDelegator parent) {
            return this.parentLogger == parent;
        }
    }



    private final String name;

    /**
     * Configuration value. Set from {@link LogEventFactory}
     */
    protected LogEventFilter ownFilter;
    /**
     * Configuration value. Set from {@link LogEventFactory}
     */
    protected LogEventObserver ownObserver = new NullLogEventObserver();
    /**
     * Configuration value. Set from {@link LogEventFactory}
     */
    protected boolean inheritParentObserver = true;

    @Override
    public boolean isConfigured() {
        return ownFilter != null || !(ownObserver instanceof NullLogEventObserver) || !inheritParentObserver;
    }

    /**
     * Calculated value. If {@link #inheritParentObserver} is true,
     * combines {@link #ownObserver} with parent {@link #observer},
     * otherwise set to {@link #ownObserver}.
     */
    protected LogEventObserver observer;
    
    /**
     * Calculated value. If {@link #ownFilter} is set, uses this
     * otherwise uses parent's {@link #effectiveFilter}.
     */
    protected LogEventFilter effectiveFilter;

    public LogEventObserver getOwnObserver() {
        return ownObserver;
    }

    @Override
    public LogEventFilter getEffectiveFilter() {
        return effectiveFilter;
    }

    @Override
    public LogEventObserver getTraceObservers() {
        return traceEventGenerator.getObservers();
    }

    @Override
    public LogEventObserver getDebugObservers() {
        return debugEventGenerator.getObservers();
    }

    @Override
    public LogEventObserver getInfoObservers() {
        return infoEventGenerator.getObservers();
    }

    @Override
    public LogEventObserver getWarnObservers() {
        return warnEventGenerator.getObservers();
    }

    @Override
    public LogEventObserver getErrorObservers() {
        return errorEventGenerator.getObservers();
    }

    private LogEventGenerator traceEventGenerator;
    private LogEventGenerator debugEventGenerator;
    private LogEventGenerator infoEventGenerator;
    private LogEventGenerator warnEventGenerator;
    private LogEventGenerator errorEventGenerator;

    public LoggerDelegator(String name) {
        this.name = name;
    }

    public static LoggerDelegator rootLogger() {
        return new RootLoggerDelegator();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return this.traceEventGenerator.isEnabled();
    }

    @Override
    public void trace(String msg) {
        traceEventGenerator.log(msg);
    }

    @Override
    public void trace(String format, Object arg) {
        traceEventGenerator.log(format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        traceEventGenerator.log(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        traceEventGenerator.log(format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        traceEventGenerator.log(msg, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return this.traceEventGenerator.isEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String msg) {
        traceEventGenerator.log(marker, msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        traceEventGenerator.log(marker, format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        traceEventGenerator.log(marker, format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        traceEventGenerator.log(marker, format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        traceEventGenerator.log(marker, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return this.debugEventGenerator.isEnabled();
    }

    @Override
    public void debug(String msg) {
        debugEventGenerator.log(msg);
    }

    @Override
    public void debug(String format, Object arg) {
        debugEventGenerator.log(format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        debugEventGenerator.log(format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        debugEventGenerator.log(format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        debugEventGenerator.log(msg, t);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return this.debugEventGenerator.isEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String msg) {
        debugEventGenerator.log(marker, msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        debugEventGenerator.log(marker, format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        debugEventGenerator.log(marker, format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        debugEventGenerator.log(marker, format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        debugEventGenerator.log(marker, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return this.infoEventGenerator.isEnabled();
    }

    @Override
    public void info(String msg) {
        infoEventGenerator.log(msg);
    }

    @Override
    public void info(String format, Object arg) {
        infoEventGenerator.log(format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        infoEventGenerator.log(format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        infoEventGenerator.log(format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        infoEventGenerator.log(msg, t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return this.infoEventGenerator.isEnabled(marker);
    }

    @Override
    public void info(Marker marker, String msg) {
        infoEventGenerator.log(marker, msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        infoEventGenerator.log(marker, format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        infoEventGenerator.log(marker, format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        infoEventGenerator.log(marker, format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        infoEventGenerator.log(marker, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return this.warnEventGenerator.isEnabled();
    }

    @Override
    public void warn(String msg) {
        warnEventGenerator.log(msg);
    }

    @Override
    public void warn(String format, Object arg) {
        warnEventGenerator.log(format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
        warnEventGenerator.log(format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        warnEventGenerator.log(format, arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable t) {
        warnEventGenerator.log(msg, t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return this.warnEventGenerator.isEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String msg) {
        warnEventGenerator.log(marker, msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        warnEventGenerator.log(marker, format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        warnEventGenerator.log(marker, format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        warnEventGenerator.log(marker, format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        warnEventGenerator.log(marker, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return this.errorEventGenerator.isEnabled();
    }

    @Override
    public void error(String msg) {
        errorEventGenerator.log(msg);
    }

    @Override
    public void error(String format, Object arg) {
        errorEventGenerator.log(format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        errorEventGenerator.log(format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        errorEventGenerator.log(format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        errorEventGenerator.log(msg, t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return this.errorEventGenerator.isEnabled(marker);
    }

    @Override
    public void error(Marker marker, String msg) {
        errorEventGenerator.log(marker, msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        errorEventGenerator.log(marker, format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        errorEventGenerator.log(marker, format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        errorEventGenerator.log(marker, format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        errorEventGenerator.log(marker, msg, t);
    }

    @Override
    public LogEventFilter getOwnFilter() {
        return ownFilter;
    }

    @Override
    public String getObserver() {
        return observer.toString();
    }

    @Override
    public void log(Marker marker, String fqcn, int levelInt, String message, Object[] argArray, Throwable t) {
        Level level = getLevel(levelInt);
        getLogger(level).log(marker, message, argArray, t);
    }

    @Override
    public LogEventGenerator getLogger(Level level) {
        switch (level) {
            case ERROR: return errorEventGenerator;
            case WARN: return warnEventGenerator;
            case INFO: return infoEventGenerator;
            case DEBUG: return debugEventGenerator;
            case TRACE: return traceEventGenerator;
        }
        throw new IllegalArgumentException("Illegal level " + level);
    }

    public static Level getLevel(int levelInt) {
        return Stream.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)
                        .filter(l -> l.toInt() >= levelInt)
                        .findFirst().orElse(Level.ERROR);
    }
    
    public void setFilter(LogEventFilter filter) {
        this.ownFilter = filter;
    }

    public void setLevelThreshold(Level ownFilter) {
        this.ownFilter = new LevelThresholdFilter(ownFilter);
    }

    public void setOwnObserver(LogEventObserver ownObserver, boolean inheritParentObserver) {
        this.ownObserver = ownObserver;
        this.inheritParentObserver = inheritParentObserver;
    }

    public void reset() {
        this.ownObserver = new NullLogEventObserver();
        this.inheritParentObserver = true;
        this.ownFilter = null;

        this.observer = null;
    }

    public abstract void refresh();

    protected void refreshEventGenerators() {
        this.errorEventGenerator = createEventGenerator(Level.ERROR);
        this.warnEventGenerator  = createEventGenerator(Level.WARN);
        this.infoEventGenerator = createEventGenerator(Level.INFO);
        this.debugEventGenerator = createEventGenerator(Level.DEBUG);
        this.traceEventGenerator = createEventGenerator(Level.TRACE);
    }

    private LogEventGenerator createEventGenerator(Level level) {
        return LogEventGenerator.create(getName(), level, effectiveFilter.filterObserverOnLevel(level, observer));
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + name + ",filter=" + ownFilter + ",ownObserver=" + ownObserver + "}";
    }

    public LogEventObserver setObserver(LogEventObserver observer, boolean inheritParentObserver) {
        LogEventObserver oldObserver = this.ownObserver;
        setOwnObserver(observer, inheritParentObserver);
        return oldObserver;
    }

    public void addObserver(LogEventObserver observer) {
        this.ownObserver = CompositeLogEventObserver.combine(observer, ownObserver);
    }

    public void replaceObserver(LogEventObserver observer) {
        this.ownObserver = observer;
    }

    public LoggerDelegator getChildLogger(String name) {
        return new CategoryLoggerDelegator(name, this);
    }

    public abstract boolean hasParent(LoggerDelegator parent);
}
