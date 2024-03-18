package org.logevents.core;

import org.logevents.LogEventObserver;
import org.logevents.observers.ConditionalLogEventObserver;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Internal interface that is used by all calls to {@link org.slf4j.Logger#error}, {@link org.slf4j.Logger#warn},
 *  {@link org.slf4j.Logger#info},  {@link org.slf4j.Logger#debug} and {@link org.slf4j.Logger#trace}.
 *  There are three implementations: {@link NullLoggingEventGenerator} is used for all log events that are
 *  below the logger threshold, and {@link LevelLoggingEventGenerator} is used for all log events that are
 *  to be logged
 */
public interface LogEventGenerator {

    static LogEventGenerator create(String loggerName, Level level, LogEventObserver observer) {
        if (observer instanceof NullLogEventObserver) {
            return new NullLoggingEventGenerator();
        } else if (observer instanceof AbstractFilteredLogEventObserver) {
            LogEventPredicate condition = ((AbstractFilteredLogEventObserver) observer).getCondition();
            return conditionalLogEventGenerator(loggerName, level, observer, condition);
        } else if (observer instanceof ConditionalLogEventObserver) {
            LogEventPredicate condition = ((ConditionalLogEventObserver) observer).getCondition();
            return conditionalLogEventGenerator(loggerName, level, observer, condition);
        } else {
            return new LevelLoggingEventGenerator(loggerName, level, observer);
        }
    }

    static LogEventGenerator conditionalLogEventGenerator(String loggerName, Level level, LogEventObserver observer, LogEventPredicate condition) {
        if (condition instanceof LogEventPredicate.AlwaysCondition) {
            return new LevelLoggingEventGenerator(loggerName, level, observer);
        }
        if (condition instanceof LogEventPredicate.NeverCondition) {
            return new NullLoggingEventGenerator();
        }
        return new LevelLoggingEventGenerator(loggerName, level, observer);
    }

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

    LogEventObserver getObservers();

    LoggingEventBuilder atLevel();
}
