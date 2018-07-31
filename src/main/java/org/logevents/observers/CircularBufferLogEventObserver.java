package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.util.CircularBuffer;

public class CircularBufferLogEventObserver implements LogEventObserver {

    private CircularBuffer<LogEvent> circularBuffer = new CircularBuffer<>();

    @Override
    public void logEvent(LogEvent logEvent) {
        logEvent.getCallerLocation();
        this.circularBuffer.add(logEvent);
    }

    public CircularBuffer<LogEvent> getEvents() {
        return circularBuffer;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{size=" + circularBuffer.size() + "}";
    }

    public String singleMessage() {
        if (circularBuffer.size() != 1) {
            throw new IllegalStateException("Expected 1 message, but was " + circularBuffer.size());
        }
        return circularBuffer.get(0).getMessage();
    }
}
