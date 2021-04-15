package org.logevents.impl;

import org.logevents.LogEventObserver;
import org.slf4j.event.Level;

public class LevelThresholdFilter implements LogEventFilter {
    private final Level threshold;

    public LevelThresholdFilter(Level threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("Threshold must be set");
        }
        this.threshold = threshold;
    }

    @Override
    public LogEventObserver filterObserverOnLevel(Level level, LogEventObserver observer) {
        return observer.filteredOn(level, threshold);
    }

    @Override
    public Level getThreshold() {
        return threshold;
    }

    @Override
    public String toString() {
        return "LevelThresholdFilter{" + threshold + '}';
    }
}
