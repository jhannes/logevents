package org.logevents.observers;

import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

/**
 * A {@link LogEventObserver} that forwards all log events to a delegate observer
 * if they have a log level equal to or more severe than the {@link #threshold}.
 *
 * @author Johannes Brodwall
 */
public class LevelThresholdConditionalObserver implements LogEventObserver {

    private Level threshold;
    private LogEventObserver delegate;

    public LevelThresholdConditionalObserver(Level threshold, LogEventObserver delegate) {
        this.threshold = threshold;
        this.delegate = delegate;
    }

    public LevelThresholdConditionalObserver(Configuration configuration) {
        threshold = Level.valueOf(configuration.getString("threshold"));
        delegate = configuration.createInstance("delegate", LogEventObserver.class, "org.logevents.observers");
        LogEventStatus.getInstance().addInfo(this, "Configured " + configuration.getPrefix());
    }

    public LevelThresholdConditionalObserver(Properties configuration, String prefix) {
        this(new Configuration(configuration, prefix));
    }

    @Override
    public void logEvent(LogEvent event) {
        if (threshold.toInt() <= event.getLevel().toInt()) {
            delegate.logEvent(event);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + threshold + " -> " + delegate + "}";
    }
}
