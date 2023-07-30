package org.logevents.formatters;

import org.logevents.LogEvent;
import org.logevents.LogEventFormatter;
import org.logevents.config.Configuration;
import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.mdc.DynamicMDC;
import org.logevents.util.JsonUtil;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Used to format a LogEvent as JSON for stdout or network observers.
 * Example configuration:
 *
 * <pre>
 * observer.foo.formatter=JsonLogEventFormatter
 * observer.foo.formatter.excludedMdcKeys=secret,ipAddress
 * observer.foo.formatter.properties.environment=staging
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class JsonLogEventFormatter implements LogEventFormatter {

    protected MessageFormatter messageFormatter = new MessageFormatter();
    protected ExceptionFormatter exceptionFormatter = new ExceptionFormatter();
    protected MdcFilter mdcFilter = MdcFilter.INCLUDE_ALL;
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private String hostname;
    private String applicationName;
    private final Map<String, String> additionalProperties = new HashMap<>();

    @SuppressWarnings("unused")
    public JsonLogEventFormatter() {
        this(new Configuration());
    }

    public JsonLogEventFormatter(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public JsonLogEventFormatter(Configuration configuration) {
        configure(configuration);
        configuration.checkForUnknownFields();
    }

    /**
     * reads applicationName, nodeName, messageFormatter, mdcFilter, dateTimeFormat and
     * properties
     */
    @Override
    public void configure(Configuration configuration) {
        applicationName = configuration.getApplicationName();
        hostname = configuration.getNodeName();
        messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class);
        mdcFilter = configuration.getMdcFilter();
        dateTimeFormatter = configuration
                .optionalString("dateTimeFormat").map(DateTimeFormatter::ofPattern)
                .orElse(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        for (String name : configuration.listProperties("properties")) {
            additionalProperties.put(name, configuration.getString("properties." + name));
        }
    }

    public String apply(LogEvent e) {
        return JsonUtil.toIndentedJson(toJsonObject(e));
    }

    public Map<String, Object> toJsonObject(LogEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>(additionalProperties);

        payload.put("log.level", event.getLevel().toString());
        payload.put("log.logger", event.getLoggerName());
        payload.put("@timestamp", event.getZonedDateTime().format(dateTimeFormatter));
        payload.put("messageFormat", event.getMessage());
        payload.put("process.thread.name", event.getThreadName());
        if (event.getThrowable() != null) {
            payload.put("message", event.getMessage(messageFormatter) + " " + event.getThrowable());
            payload.put("error", toJsonObject(event.getThrowable(), exceptionFormatter));
        } else {
            payload.put("message", event.getMessage(messageFormatter));
        }

        if (event.getMarker() != null) {
            payload.put("tags", Collections.singletonList(event.getMarker().getName()));
        }
        payload.put("service.name", applicationName);
        payload.put("host.name", hostname);
        payload.put("levelInt", event.getLevel().toInt());
        updateMdc(event, payload);

        return payload;
    }

    public static Map<String, Object> toJsonObject(Throwable throwable, ExceptionFormatter exceptionFormatter) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("class", throwable.getClass().getName());
        payload.put("message", throwable.getMessage());
        payload.put("stack_trace", exceptionFormatter.format(throwable));
        return payload;
    }

    private void updateMdc(LogEvent event, Map<String, Object> payload) {
        Map<String, Object> mdc = new HashMap<>();
        payload.put("mdc", mdc);

        for (Map.Entry<String, String> mdcEntry : event.getStaticMdcProperties().entrySet()) {
            if (mdcFilter.isKeyIncluded(mdcEntry.getKey())) {
                mdc.put(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }

        for (DynamicMDC dynamicMDC : event.getDynamicMdcProperties().values()) {
            dynamicMDC.populateJsonEvent(payload, mdcFilter, exceptionFormatter);
        }

        if (mdc.isEmpty()) {
            payload.remove("mdc");
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
