package org.logevents.observers;

import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ConfigUtil;
import org.slf4j.event.Level;

public class LevelThresholdConditionalObserver implements LogEventObserver {

    private Level threshold;
    private LogEventObserver delegate;

    public LevelThresholdConditionalObserver(Properties configuration, String prefix) {
        threshold = Level.valueOf(configuration.getProperty(prefix + ".threshold"));
        delegate = ConfigUtil.create(prefix + ".delegate", "org.logevents.observers", configuration);
        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

    public LevelThresholdConditionalObserver(Level threshold, LogEventObserver delegate) {
        this.threshold = threshold;
        this.delegate = delegate;
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
