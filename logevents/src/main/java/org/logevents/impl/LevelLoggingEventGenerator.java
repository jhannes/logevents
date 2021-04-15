package org.logevents.impl;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.Marker;
import org.slf4j.event.Level;

class LevelLoggingEventGenerator implements LogEventGenerator {

    private final LogEventObserver observer;
    private final Level level;
    private final String loggerName;

    LevelLoggingEventGenerator(String loggerName, Level level, LogEventObserver observer) {
        this.loggerName = loggerName;
        this.level = level;
        this.observer = observer;
    }

    @Override
    public boolean isEnabled() {
        return observer.isEnabled();
    }

    @Override
    public boolean isEnabled(Marker marker) {
        return observer.isEnabled(marker);
    }

    @Override
    public void log(String msg) {
        log(createEvent(msg, null, new Object[0]));
    }

    @Override
    public void log(String format, Object arg) {
        log(createEvent(format, null, new Object[]{arg}));
    }

    @Override
    public void log(String format, Throwable t) {
        log(createEvent(format, null, new Object[]{t}));
    }

    @Override
    public void log(String format, Object arg1, Object arg2) {
        log(createEvent(format, null, new Object[]{arg1, arg2}));
    }

    @Override
    public void log(String format, Object... arg) {
        log(createEvent(format, null, arg));
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

    @Override
    public LogEventObserver getObservers() {
        return observer;
    }

    private LogEvent createEvent(String format, Marker marker, Object[] args) {
        return new LogEvent(this.loggerName, this.level, marker, format, args);
    }

    public void log(LogEvent logEvent) {
        try {
            observer.logEvent(logEvent);
        } catch (Exception e) {
            LogEventStatus.getInstance().addError(observer, "Failed to log to observer", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "observer=" + observer +
                '}';
    }
}
