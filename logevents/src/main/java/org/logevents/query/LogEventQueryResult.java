package org.logevents.query;

import org.logevents.LogEvent;

import java.util.stream.Stream;

public interface LogEventQueryResult {
    Stream<LogEvent> stream();

    LogEventSummary getSummary();
}
