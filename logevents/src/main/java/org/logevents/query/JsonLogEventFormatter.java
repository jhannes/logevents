package org.logevents.query;

import org.logevents.LogEvent;
import org.logevents.extend.servlets.JsonExceptionFormatter;
import org.logevents.extend.servlets.JsonMessageFormatter;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.slf4j.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonLogEventFormatter implements Function<LogEvent, Map<String, Object>> {
    private final MessageFormatter messageFormatter = new MessageFormatter();
    private final JsonExceptionFormatter exceptionFormatter = new JsonExceptionFormatter();
    private final JsonMessageFormatter jsonFormatter = new JsonMessageFormatter();
    private String nodeName;
    private String applicationName;

    public JsonLogEventFormatter(String nodeName, String applicationName) {
        this.nodeName = nodeName;
        this.applicationName = applicationName;
    }

    @Override
    public Map<String, Object> apply(LogEvent event) {
        Map<String, Object> jsonEvent = new HashMap<>();

        jsonEvent.put("thread", event.getThreadName());
        jsonEvent.put("time", event.getInstant().toString());
        jsonEvent.put("logger", event.getLoggerName());
        jsonEvent.put("abbreviatedLogger", event.getAbbreviatedLoggerName(0));
        jsonEvent.put("level", event.getLevel().name());
        jsonEvent.put("levelIcon", JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()));
        jsonEvent.put("formattedMessage", messageFormatter.format(event.getMessage(), event.getArgumentArray()));
        jsonEvent.put("messageTemplate", event.getMessage());
        jsonEvent.put("message", jsonFormatter.format(event.getMessage(), event.getArgumentArray()));
        jsonEvent.put("marker", Optional.ofNullable(event.getMarker()).map(Marker::getName).orElse(null));
        jsonEvent.put("arguments", Stream.of(event.getArgumentArray()).map(Object::toString).collect(Collectors.toList()));
        jsonEvent.put("mdc", formatMdc(event));
        jsonEvent.put("node", this.nodeName);
        jsonEvent.put("application", this.applicationName);

        if (event.getThrowable() != null) {
            jsonEvent.put("throwable", event.getThrowable().toString());
            jsonEvent.put("stackTrace", exceptionFormatter.createStackTrace(event.getThrowable()));
        }
        return jsonEvent;
    }

    public List<Map<String, String>> formatMdc(LogEvent event) {
        List<Map<String, String>> mdc = new ArrayList<>();
        for (Map.Entry<String, String> entry : event.getMdcProperties().entrySet()) {
            Map<String, String> mdcEntry = new HashMap<>();
            mdcEntry.put("name", entry.getKey());
            mdcEntry.put("value", entry.getValue());
            mdc.add(mdcEntry);
        }
        return mdc;
    }
}
