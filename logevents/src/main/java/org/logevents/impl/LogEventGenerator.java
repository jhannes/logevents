package org.logevents.impl;

import org.logevents.LogEventObserver;
import org.logevents.observers.AbstractFilteredLogEventObserver;
import org.logevents.observers.ConditionalLogEventObserver;
import org.logevents.observers.NullLogEventObserver;
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

    static LogEventGenerator create(String name, Level level, LogEventObserver observer) {
        if (observer instanceof NullLogEventObserver) {
            return new NullLoggingEventGenerator();
        } else if (observer instanceof AbstractFilteredLogEventObserver) {
            return new ConditionalLogEventGenerator(name, level, ((AbstractFilteredLogEventObserver)observer).getCondition(), observer);
        } else if (observer instanceof ConditionalLogEventObserver) {
            ConditionalLogEventObserver conditionalObserver = (ConditionalLogEventObserver) observer;
            return new ConditionalLogEventGenerator(name, level, conditionalObserver.getCondition(), conditionalObserver.getObserver());
        } else {
            return new LevelLoggingEventGenerator(name, level, observer);
        }
    }

    LogEventObserver getObservers();
}
