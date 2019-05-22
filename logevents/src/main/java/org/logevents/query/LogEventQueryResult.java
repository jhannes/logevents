package org.logevents.query;

import org.logevents.LogEvent;

import java.util.Collection;
import java.util.stream.Stream;

public class LogEventQueryResult {
    private final Collection<LogEvent> events;
    private final LogEventSummary summary;

    public LogEventQueryResult(Collection<LogEvent> events, LogEventSummary summary) {
        this.events = events;
        this.summary = summary;
    }

    public Collection<LogEvent> getEvents() {
        return events;
    }

    public LogEventSummary getSummary() {
        return summary;
    }

    public Stream<LogEvent> stream() {
        return getEvents().stream();
    }
}
