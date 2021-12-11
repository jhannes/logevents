package org.logevents.core;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.core.LogEventPredicate;
import org.logevents.core.NullLogEventObserver;
import org.slf4j.event.Level;

/**
 * A {@link LogEventObserver} that forwards all log events to a delegate observer
 * if they have a log level equal to or more severe than the {@link #threshold},
 * <strong>even if the logger has a lower logging threshold</strong>. Used to install
 * global root loggers
 *
 * @author Johannes Brodwall
 */
public class FixedLevelThresholdConditionalObserver implements LogEventObserver {

    private Level threshold;
    private LogEventObserver delegate;

    public FixedLevelThresholdConditionalObserver(Level threshold, LogEventObserver delegate) {
        this.threshold = threshold;
        this.delegate = delegate;
    }

    @Override
    public void logEvent(LogEvent event) {
        if (!event.isBelowThreshold(threshold)) {
            delegate.logEvent(event);
        }
    }

    public Level getThreshold() {
        return threshold;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + threshold + " -> " + delegate + "}";
    }

    @Override
    public LogEventObserver filteredOn(Level level, LogEventPredicate predicate) {
        boolean shouldLog = level.compareTo(threshold) <= 0;
        return shouldLog ? delegate : new NullLogEventObserver();
    }
}
