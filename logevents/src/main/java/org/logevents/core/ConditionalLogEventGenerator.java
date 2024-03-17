package org.logevents.core;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.NOPLoggingEventBuilder;

/**
 * Forwards the log event to it's {@link #observer} if {@link LogEventPredicate#test()} returns true.
 * If the event is logged with a marker, {@link LogEventPredicate#test(Marker)} is used to check if
 * the message should be logged. MDC and Markers are the most likely things to test for.
 *
 * @see LogEventFilter
 */
public class ConditionalLogEventGenerator implements LogEventGenerator {
    private final String loggerName;
    private final Level level;
    private final LogEventObserver observer;

    private ConditionalLogEventGenerator(String loggerName, Level level, LogEventPredicate condition, LogEventObserver observer) {
        this.loggerName = loggerName;
        this.level = level;
        this.observer = observer.filteredOn(level, condition);
    }

    static LogEventGenerator create(String loggerName, Level level, LogEventObserver observer, LogEventPredicate condition) {
        if (condition instanceof LogEventPredicate.AlwaysCondition) {
            return new LevelLoggingEventGenerator(loggerName, level, observer);
        }
        if (condition instanceof LogEventPredicate.NeverCondition) {
            return new NullLoggingEventGenerator();
        }
        return new ConditionalLogEventGenerator(loggerName, level, condition, observer);
    }

    @Override
    public boolean isEnabled() {
        return observer.isEnabled();
    }

    @Override
    public LoggingEventBuilder atLevel() {
        return isEnabled() ? new LogEventBuilder(loggerName, level, observer) : NOPLoggingEventBuilder.singleton();
    }

    @Override
    public boolean isEnabled(Marker marker) {
        return observer.isEnabled(marker);
    }

    @Override
    public void log(String msg) {
        if (isEnabled()) {
            log(createEvent(msg, null, new Object[0]));
        }
    }

    @Override
    public void log(String format, Object arg) {
        if (isEnabled()) {
            log(createEvent(format, null, new Object[]{arg}));
        }
    }

    @Override
    public void log(String format, Throwable t) {
        if (isEnabled()) {
            log(createEvent(format, null, new Object[]{t}));
        }
    }

    @Override
    public void log(String format, Object arg1, Object arg2) {
        if (isEnabled()) {
            log(createEvent(format, null, new Object[]{arg1, arg2}));
        }
    }

    @Override
    public void log(String format, Object... arg) {
        if (isEnabled()) {
            log(createEvent(format, null, arg));
        }
    }

    @Override
    public void log(Marker marker, String msg) {
        if (isEnabled(marker)) {
            log(createEvent(msg, marker, new Object[0]));
        }
    }

    @Override
    public void log(Marker marker, String format, Object arg) {
        if (isEnabled(marker)) {
            log(createEvent(format, marker, new Object[]{arg}));
        }
    }

    @Override
    public void log(Marker marker, String format, Object arg1, Object arg2) {
        if (isEnabled(marker)) {
            log(createEvent(format, marker, new Object[]{arg1, arg2}));
        }
    }

    @Override
    public void log(Marker marker, String format, Object... args) {
        if (isEnabled(marker)) {
            log(createEvent(format, marker, args));
        }
    }

    @Override
    public LogEventObserver getObservers() {
        return observer;
    }

    private LogEvent createEvent(String format, Marker marker, Object[] args) {
        return new LogEvent(this.loggerName, this.level, marker, format, args);
    }

    private void log(LogEvent logEvent) {
        try {
            observer.logEvent(logEvent);
        } catch (Exception e) {
            LogEventStatus.getInstance().addError(observer, "Failed to log to observer", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{observer=" + observer + '}';
    }
}
