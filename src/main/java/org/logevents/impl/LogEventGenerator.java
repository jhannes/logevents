package org.logevents.impl;

import org.logevents.LogEventObserver;
import org.slf4j.Marker;
import org.slf4j.event.Level;

public interface LogEventGenerator {

    boolean isEnabled();

    boolean isEnabled(Marker marker);

    void log(String msg);

    void log(String format, Object arg);

    void log(String format, Throwable t);

    void log(String format, Object arg1, Object arg2);

    void log(String format, Object... args);

    void log(Marker marker, String msg);

    void log(Marker marker, String format, Object arg);

    void log(Marker marker, String format, Object arg1, Object arg2);

    void log(Marker marker, String format, Object... args);

    static LogEventGenerator create(String name, Level levelThreshold, Level level, LogEventObserver observer) {
        if (levelThreshold != null && levelThreshold.compareTo(level) >= 0) {
            return new LevelLoggingEventGenerator(name, level, observer);
        } else {
            return new NullLoggingEventGenerator();
        }
    }
}
