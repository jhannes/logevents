package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.extend.junit.LogEventRule;
import org.logevents.util.CircularBuffer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects log events in an internal circular buffer. When the buffer is filled
 * the first messages in the buffer are dropped. This is useful for reporting
 * log events with a servlet or similar or for internal testing.
 * @see LogEventRule for more on testing.
 *
 * @author Johannes Brodwall
 */
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
            throw new AssertionError("Expected 1 message, but was " + circularBuffer.size());
        }
        return circularBuffer.get(0).getMessage();
    }

    public Throwable singleException() {
        if (circularBuffer.size() != 1) {
            throw new AssertionError("Expected 1 message, but was " + circularBuffer.size());
        }
        return circularBuffer.get(0).getThrowable();
    }

    public List<String> getMessages() {
        return circularBuffer.stream().map(LogEvent::getMessage).collect(Collectors.toList());
    }
}
