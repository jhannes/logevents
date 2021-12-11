package org.logevents.core;

import org.logevents.LogEventObserver;
import org.logevents.core.ConditionalLogEventGenerator;
import org.logevents.core.LevelLoggingEventGenerator;
import org.logevents.core.NullLoggingEventGenerator;
import org.slf4j.Marker;

/**
 * Internal interface that is used by all calls to {@link org.slf4j.Logger#error}, {@link org.slf4j.Logger#warn},
 *  {@link org.slf4j.Logger#info},  {@link org.slf4j.Logger#debug} and {@link org.slf4j.Logger#trace}.
 *  There are three implementations: {@link NullLoggingEventGenerator} is used for all log events that are
 *  below the logger threshold, {@link LevelLoggingEventGenerator} is used for all log events that are
 *  to be logged and {@link ConditionalLogEventGenerator} is used when there is a more complex condition
 */
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

    LogEventObserver getObservers();
}
