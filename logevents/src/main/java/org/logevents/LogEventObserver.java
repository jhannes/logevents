package org.logevents;

import org.logevents.observers.AbstractBatchingLogEventObserver;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.FileLogEventObserver;
import org.logevents.observers.NullLogEventObserver;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.List;

/**
 * The main interface of the Log Event framework. Implement this
 * interface to process log events in your own way. The most used included
 * observers are {@link FileLogEventObserver} (used for file output),
 * {@link ConsoleLogEventObserver} (to standard out, with ANSI colors),
 * {@link CompositeLogEventObserver} (used to call multiple observers),
 * {@link CircularBufferLogEventObserver} (used to keep an internal buffer of log events)
 * and {@link AbstractBatchingLogEventObserver} (used to batch up multiple events before
 * processing).
 *
 * @author Johannes Brodwall.
 *
 */
public interface LogEventObserver {

    void logEvent(LogEvent logEvent);

    default LogEventObserver filteredOn(Level level, Level configuredThreshold) {
        if (configuredThreshold == null || configuredThreshold.compareTo(level) < 0) {
            return new NullLogEventObserver();
        }
        return this;
    }

    default List<LogEventObserver> toList() {
        return Collections.singletonList(this);
    }

    default boolean isEnabled(Marker marker) {
        return true;
    }

    default boolean isEnabled() {
        return true;
    }
}
