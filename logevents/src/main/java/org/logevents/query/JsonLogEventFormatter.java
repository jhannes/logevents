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
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonLogEventFormatter implements Function<LogEvent, Map<String, Object>> {
    private final MessageFormatter messageFormatter = new MessageFormatter();
    private final JsonExceptionFormatter exceptionFormatter;
    private final JsonMessageFormatter jsonFormatter;
    private String nodeName;
    private String applicationName;

    public JsonLogEventFormatter(String nodeName, String applicationName, JsonMessageFormatter jsonFormatter, JsonExceptionFormatter exceptionFormatter) {
        this.nodeName = nodeName;
        this.applicationName = applicationName;
        this.exceptionFormatter = exceptionFormatter;
        this.jsonFormatter = jsonFormatter;
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
        jsonEvent.put("formattedMessage", event.getMessage(messageFormatter));
        jsonEvent.put("messageTemplate", event.getMessage());
        jsonEvent.put("message", jsonFormatter.format(event.getMessage(), event.getArgumentArray()));
        jsonEvent.put("marker", Optional.ofNullable(event.getMarker()).map(Marker::getName).orElse(null));
        jsonEvent.put("arguments", Stream.of(event.getArgumentArray()).map(o -> o != null ? o.toString() : null).collect(Collectors.toList()));
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
        for (String name : new TreeSet<>(event.getMdcProperties().keySet())) {
            Map<String, String> mdcEntry = new HashMap<>();
            mdcEntry.put("name", name);
            mdcEntry.put("value", event.getMdcProperties().get(name));
            mdc.add(mdcEntry);
        }
        return mdc;
    }
}
