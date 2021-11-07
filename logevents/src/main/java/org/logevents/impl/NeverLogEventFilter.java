package org.logevents.impl;

import org.logevents.LogEventObserver;
import org.logevents.observers.NullLogEventObserver;
import org.slf4j.event.Level;

public class NeverLogEventFilter implements LogEventFilter {
    @Override
    public LogEventObserver filterObserverOnLevel(Level level, LogEventObserver observer) {
        return new NullLogEventObserver();
    }

    @Override
    public Level getThreshold() {
        return null;
    }
}
