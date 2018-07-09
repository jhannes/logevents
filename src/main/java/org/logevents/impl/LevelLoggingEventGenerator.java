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
        log(new LogEvent(this.loggerName, this.level, null, msg, new Object[0]));
    }

    @Override
    public void log(String format, Object arg) {
        log(new LogEvent(this.loggerName, this.level, null, format, new Object[] { arg }));
    }

    @Override
    public void log(String format, Throwable t) {
        log(new LogEvent(this.loggerName, this.level, null, format, new Object[] { t }));
    }

    @Override
    public void log(String format, Object arg1, Object arg2) {
        log(new LogEvent(this.loggerName, this.level, null, format, new Object[] { arg1, arg2 }));
    }

    @Override
    public void log(String format, Object... arg) {
        log(new LogEvent(this.loggerName, this.level, null, format, new Object[] { arg }));
    }

    @Override
    public void log(Marker marker, String msg) {
        log(new LogEvent(this.loggerName, this.level, marker, msg, new Object[0]));
    }

    @Override
    public void log(Marker marker, String format, Object arg) {
        log(new LogEvent(this.loggerName, this.level, marker, format, new Object[] { arg }));
    }

    @Override
    public void log(Marker marker, String format, Object arg1, Object arg2) {
        log(new LogEvent(this.loggerName, this.level, marker, format, new Object[] { arg1, arg2 }));
    }

    @Override
    public void log(Marker marker, String format, Object... args) {
        log(new LogEvent(this.loggerName, this.level, marker, format, args));
    }

    private void log(LogEvent logEvent) {
        observer.logEvent(logEvent);
    }

}
