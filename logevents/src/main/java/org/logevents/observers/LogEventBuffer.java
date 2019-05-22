package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.query.LogEventFilter;
import org.logevents.query.LogEventQueryResult;
import org.logevents.query.LogEventSummary;
import org.logevents.util.CircularBuffer;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class LogEventBuffer implements LogEventObserver, LogEventSource {
    /**
     * In order to survive reload of configuration, it's useful to have a static message buffer
     */
    private final static EnumMap<Level, CircularBuffer<LogEvent>> messages = new EnumMap<>(Level.class);

    public LogEventBuffer(int capacity) {
        for (Level level : Level.values()) {
            messages.put(level, new CircularBuffer<>(capacity));
        }
    }

    public LogEventBuffer() {
        this(2000);
    }

    private Collection<LogEvent> filter(Level threshold, Instant start, Instant end) {
        List<LogEvent> logEvents = new ArrayList<>();
        for (Map.Entry<Level, ? extends Collection<LogEvent>> entry : messages.entrySet()) {
            if (entry.getKey().compareTo(threshold) <= 0) {
                // TODO It may be worth the effort to implement a binary search here
                entry.getValue().stream()
                        .filter(event -> event.getInstant().isAfter(start) && event.getInstant().isBefore(end))
                        .forEach(logEvents::add);
            }
        }
        logEvents.sort(Comparator.comparing(LogEvent::getInstant));
        return logEvents;
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        messages.get(logEvent.getLevel()).add(logEvent);
    }

    public LogEventQueryResult query(LogEventFilter filter) {
        Collection<LogEvent> allEvents = filter(filter.getThreshold(), filter.getStartTime(), filter.getEndTime());
        LogEventSummary summary = new LogEventSummary();
        allEvents.forEach(summary::add);
        return new LogEventQueryResult(allEvents, summary);
    }
}
