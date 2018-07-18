package org.logevents.observers;

import java.util.Collection;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.util.CircularBuffer;

public class CircularBufferLogEventObserver implements LogEventObserver {

    private CircularBuffer<LogEvent> circularBuffer = new CircularBuffer<>();

    @Override
    public void logEvent(LogEvent logEvent) {
        this.circularBuffer.add(logEvent);
    }

    public Collection<LogEvent> getEvents() {
        return circularBuffer;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{size=" + circularBuffer.size() + "}";
    }
}
