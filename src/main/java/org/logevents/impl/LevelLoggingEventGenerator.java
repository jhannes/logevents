package org.logevents.impl;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.slf4j.Marker;
import org.slf4j.event.Level;

class LevelLoggingEventGenerator implements LogEventGenerator {

    private LogEventObserver observer;
    private final Level level;
    private String loggerName;

    public LevelLoggingEventGenerator(String loggerName, Level level, LogEventObserver observer) {
        this.loggerName = loggerName;
        this.level = level;
        this.observer = observer;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(Marker marker) {
        return true;
    }

    @Override
    public void log(String msg) {
        log(createEvent(msg, new Object[0]));
    }

    @Override
    public void log(String format, Object arg) {
        log(createEvent(format, new Object[] { arg }));
    }

    @Override
    public void log(String format, Throwable t) {
        log(createEvent(format, new Object[] { t }));
    }

    @Override
    public void log(String format, Object arg1, Object arg2) {
        log(createEvent(format, new Object[] { arg1, arg2 }));
    }

    @Override
    public void log(String format, Object... arg) {
        log(createEvent(format, arg));
    }

    LogEvent createEvent(String format, Object[] arg) {
        return new LogEvent(this.loggerName, this.level, format, arg);
    }

    @Override
    public void log(Marker marker, String msg) {
        log(createEvent(msg, marker, new Object[0]));
    }

    @Override
    public void log(Marker marker, String format, Object arg) {
        log(createEvent(format, marker, new Object[] { arg }));
    }

    @Override
    public void log(Marker marker, String format, Object arg1, Object arg2) {
        log(createEvent(format, marker, new Object[] { arg1, arg2 }));
    }

    @Override
    public void log(Marker marker, String format, Object... args) {
        log(createEvent(format, marker, args));
    }

    private LogEvent createEvent(String format, Marker marker, Object[] args) {
        return new LogEvent(this.loggerName, this.level, marker, format, args);
    }

    private void log(LogEvent logEvent) {
        // TODO: Error handling
        observer.logEvent(logEvent);
    }

}
