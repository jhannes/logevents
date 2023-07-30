package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.core.AbstractFilteredLogEventObserver;
import org.logevents.optional.junit.LogEventRule;
import org.logevents.util.CircularBuffer;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects log events in an internal circular buffer. When the buffer is filled
 * the first messages in the buffer are dropped. This is useful for reporting
 * log events with a servlet or similar or for internal testing.
 * @see LogEventRule for more on testing.
 *
 * @author Johannes Brodwall
 */
public class CircularBufferLogEventObserver extends AbstractFilteredLogEventObserver {

    private final CircularBuffer<LogEvent> circularBuffer;

    public CircularBufferLogEventObserver(int capacity) {
        circularBuffer = new CircularBuffer<>(capacity);
    }

    public CircularBufferLogEventObserver() {
        this(200);
    }

    public CircularBufferLogEventObserver(Configuration configuration) {
        this(configuration.optionalInt("capacity").orElse(200));
        configureFilter(configuration, Level.TRACE);
        configuration.checkForUnknownFields();
    }

    public CircularBufferLogEventObserver(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    @Override
    protected void doLogEvent(LogEvent logEvent) {
        logEvent.getCallerLocation();
        this.circularBuffer.add(logEvent);
    }

    public CircularBuffer<LogEvent> getEvents() {
        return circularBuffer;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{size=" + circularBuffer.size() + ",capacity=" + circularBuffer.getCapacity() + "}";
    }

    public String singleMessage() {
        return singleLogEvent().getMessage();
    }

    public LogEvent singleLogEvent() {
        if (circularBuffer.size() != 1) {
            throw new AssertionError("Expected 1 message, but was " + circularBuffer.size());
        }
        return circularBuffer.get(0);
    }

    public Throwable singleException() {
        return singleLogEvent().getThrowable();
    }

    public List<String> getMessages() {
        return circularBuffer.stream().map(LogEvent::getMessage).collect(Collectors.toList());
    }

    public void clear() {
        circularBuffer.clear();
    }
}
