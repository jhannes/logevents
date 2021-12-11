package org.logevents.core;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.util.stream.Stream;

/**
 * A {@link LogEventObserver} that does nothing. Useful to avoid null
 * checks and null pointer exception.
 *
 * @author Johannes Brodwall
 */
public class NullLogEventObserver implements LogEventObserver {

    @Override
    public LogEventObserver filteredOn(Level level, LogEventPredicate predicate) {
        return this;
    }

    @Override
    public void logEvent(LogEvent logEvent) {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NullLogEventObserver;
    }

    @Override
    public Stream<LogEventObserver> stream() {
        return Stream.empty();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(Marker marker) {
        return false;
    }
}
