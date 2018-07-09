package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;

public class NullLogEventObserver implements LogEventObserver {

    @Override
    public void logEvent(LogEvent logEvent) {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
