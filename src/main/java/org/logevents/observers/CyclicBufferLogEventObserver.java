package org.logevents.observers;

import java.util.ArrayList;
import java.util.List;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;

public class CyclicBufferLogEventObserver implements LogEventObserver {

    private List<LogEvent> events = new ArrayList<>();

    public CyclicBufferLogEventObserver() {
    }

    public String singleMessage() {
        if (events.isEmpty()) {
            throw new IllegalStateException("Expected a log event to be received");
        } else if (events.size() > 1) {
            throw new IllegalStateException("Expected only one log event to be received, was " + events);
        }
        return events.get(0).getMessage();
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        events.add(logEvent);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{events=" + events.size() + "}";
    }


}
