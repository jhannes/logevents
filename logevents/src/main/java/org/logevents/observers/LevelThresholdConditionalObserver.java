package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.slf4j.event.Level;

import java.util.Map;

/**
 * A {@link LogEventObserver} that forwards all log events to a delegate observer
 * if they have a log level equal to or more severe than the {@link #threshold}.
 *
 * @author Johannes Brodwall
 */
public class LevelThresholdConditionalObserver implements LogEventObserver {

    private final Level threshold;
    private final LogEventObserver delegate;

    public LevelThresholdConditionalObserver(Level threshold, LogEventObserver delegate) {
        this.threshold = threshold;
        this.delegate = delegate;
    }

    public LevelThresholdConditionalObserver(Configuration configuration) {
        this(
            Level.valueOf(configuration.getString("threshold")),
            configuration.createInstance("delegate", LogEventObserver.class, "org.logevents.observers")
        );
        configuration.checkForUnknownFields();
    }

    public LevelThresholdConditionalObserver(Map<String, String> configuration, String prefix) {
        this(new Configuration(configuration, prefix));
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
    public LogEventObserver filteredOn(Level level, boolean enabledByFilter) {
        if (!enabledByFilter || getThreshold().toInt() > level.toInt()) {
            return new NullLogEventObserver();
        }
        return delegate;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + threshold + " -> " + delegate + "}";
    }
}
