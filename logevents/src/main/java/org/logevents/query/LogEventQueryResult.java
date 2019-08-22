package org.logevents.query;

import org.logevents.LogEvent;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LogEventQueryResult {
    private final LogEventSummary summary;
    private final Collection<Map<String, Object>> eventsAsJson;

    public LogEventQueryResult(LogEventSummary summary, Collection<Map<String, Object>> eventsAsJson) {
        this.eventsAsJson = eventsAsJson;
        this.summary = summary;
    }

    public Collection<LogEvent> getEvents() {
        return getEventsAsJson().stream().map(this::parse).collect(Collectors.toList());
    }

    public LogEventSummary getSummary() {
        return summary;
    }

    public Collection<Map<String, Object>> getEventsAsJson() {
        return eventsAsJson;
    }

    private LogEvent parse(Map<String, Object> json) {
        return new LogEvent(
                json.get("logger").toString(),
                Level.valueOf(json.get("level").toString()),
                json.get("thread").toString(),
                Instant.parse(json.get("time").toString()),
                json.get("marker") != null ? MarkerFactory.getMarker(json.get("marker").toString()) : null,
                json.get("messageTemplate").toString(),
                ((List)json.get("arguments")).toArray(),
                null,
                parseMdc((List<Map<String, String>>) json.get("mdc"))
        );
    }

    private Map<String, String> parseMdc(List<Map<String, String>> mdcAsJson) {
        return mdcAsJson.stream()
                .collect(Collectors.toMap(entry -> entry.get("name"), entry -> entry.get("value"), (a, b) -> b));
    }

}
