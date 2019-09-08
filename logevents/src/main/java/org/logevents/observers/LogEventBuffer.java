package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.JsonExceptionFormatter;
import org.logevents.extend.servlets.JsonMessageFormatter;
import org.logevents.query.JsonLogEventFormatter;
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
import java.util.Properties;
import java.util.stream.Collectors;

public class LogEventBuffer implements LogEventObserver, LogEventSource {
    /**
     * In order to survive reload of configuration, it's useful to have a static message buffer
     */
    private final static EnumMap<Level, CircularBuffer<LogEvent>> messages = new EnumMap<>(Level.class);
    static {
        clear();
    }

    public static void clear() {
        for (Level level : Level.values()) {
            messages.put(level, new CircularBuffer<>(2000));
        }
    }

    private final JsonLogEventFormatter jsonFormatter;

    public LogEventBuffer() {
        this(Configuration.calculateNodeName(), Configuration.calculateApplicationName(), new JsonMessageFormatter(), new JsonExceptionFormatter());
    }

    public LogEventBuffer(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public LogEventBuffer(Configuration config) {
        this(
                config.getNodeName(),
                config.getApplicationName(),
                config.createInstanceWithDefault("jsonMessageFormatter", JsonMessageFormatter.class),
                config.createInstanceWithDefault("exceptionFormatter", JsonExceptionFormatter.class));
    }

    public LogEventBuffer(String nodeName, String applicationName, JsonMessageFormatter jsonMessageFormatter, JsonExceptionFormatter exceptionFormatter) {
        this.jsonFormatter = new JsonLogEventFormatter(nodeName, applicationName, jsonMessageFormatter, exceptionFormatter);
    }

    private Collection<LogEvent> filter(Level threshold, Instant start, Instant end) {
        List<LogEvent> allEvents = new ArrayList<>();
        messages.entrySet().stream()
                .filter(level -> level.getKey().compareTo(threshold) <= 0)
                .forEach(level -> allEvents.addAll(level.getValue()));
        List<LogEvent> logEvents = allEvents.stream()
                .filter(event -> event.getInstant().isAfter(start) && event.getInstant().isBefore(end))
                .collect(Collectors.toList());
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
        return new LogEventQueryResult(summary, allEvents.stream().filter(filter).map(jsonFormatter).collect(Collectors.toList()));
    }
}

