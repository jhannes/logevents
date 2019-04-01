package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.util.CircularBuffer;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class InMemoryBufferLogEventObserver implements LogEventObserver {

    private final EnumMap<Level, CircularBuffer<LogEvent>> messages = new EnumMap<>(Level.class);

    public InMemoryBufferLogEventObserver() {
        for (Level level : Level.values()) {
            this.messages.put(level, new CircularBuffer<>());
        }
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        messages.get(logEvent.getLevel()).add(logEvent);
    }

    public Collection<LogEvent> filter(Level level, Instant start, Instant end) {
        List<LogEvent> logEvents = new ArrayList<>();
        for (Map.Entry<Level, ? extends Collection<LogEvent>> entry : messages.entrySet()) {
            if (entry.getKey().compareTo(level) <= 0) {
                // TODO It may be worth the effort to implement a binary search here
                entry.getValue().stream()
                        .filter(event -> event.getInstant().isAfter(start) && event.getInstant().isBefore(end))
                        .forEach(logEvents::add);
            }
        }
        logEvents.sort(Comparator.comparing(LogEvent::getInstant));
        return logEvents;
    }
}
