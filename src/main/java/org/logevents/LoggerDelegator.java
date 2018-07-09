package org.logevents;

import org.logevents.impl.LogEventGenerator;
import org.logevents.observers.NullLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;

abstract class LoggerDelegator implements Logger {

    private final String name;

    protected Level levelThreshold;
    protected LogEventObserver ownObserver = new NullLogEventObserver();
    protected boolean inheritParentObserver = true;

    protected LogEventObserver observer;
    protected Level effectiveThreshold;

    private LogEventGenerator traceEventGenerator;
    private LogEventGenerator debugEventGenerator;
    private LogEventGenerator infoEventGenerator;
    private LogEventGenerator warnEventGenerator;
    private LogEventGenerator errorEventGenerator;

    public LoggerDelegator(String name) {
        this.name = name;
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

    public Level getLevelThreshold() {
        return levelThreshold;
    }

    public void setLevelThreshold(Level levelThreshold) {
        this.levelThreshold = levelThreshold;
    }

    public void setOwnObserver(LogEventObserver ownObserver, boolean inheritParentObserver) {
        this.ownObserver = ownObserver;
        this.inheritParentObserver = inheritParentObserver;
    }

    void reset() {
        this.ownObserver = new NullLogEventObserver();
        this.inheritParentObserver = true;
        this.levelThreshold = null;

        this.observer = null;
    }

    abstract void refresh();

    protected void refreshEventGenerators(Level effectiveThreshold, LogEventObserver observer) {
        this.errorEventGenerator = LogEventGenerator.create(getName(), effectiveThreshold, Level.ERROR, observer);
        this.warnEventGenerator  = LogEventGenerator.create(getName(), effectiveThreshold, Level.WARN, observer);
        this.infoEventGenerator = LogEventGenerator.create(getName(), effectiveThreshold, Level.INFO, observer);
        this.debugEventGenerator = LogEventGenerator.create(getName(), effectiveThreshold, Level.DEBUG, observer);
        this.traceEventGenerator = LogEventGenerator.create(getName(), effectiveThreshold, Level.TRACE, observer);
    }

    abstract LoggerDelegator getParentLogger();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + name + ",level=" + levelThreshold + "}";
    }

    public abstract String inspect();
}
