package org.logevents.impl;

import org.logevents.LogEventObserver;
import org.slf4j.event.Level;

public interface LogEventFilter {
    LogEventObserver filterObserverOnLevel(Level level, LogEventObserver observer);

    Level getThreshold();
}
