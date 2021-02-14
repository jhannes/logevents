package org.logevents.formatting;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.util.JsonUtil;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Used to format a LogEvent as JSON for stdout or network observers.
 *
 * Example configuration:
 *
 * <pre>
 * observer.foo.formatter=JsonLogEventFormatter
 * observer.foo.formatter.excludedMdcKeys=secret,ipAddress
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class JsonLogEventFormatter implements LogEventFormatter {

    protected MessageFormatter messageFormatter = new MessageFormatter();
    protected ExceptionFormatter exceptionFormatter = new ExceptionFormatter();
    protected MdcFilter mdcFilter;
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
    private String node;
    private String applicationName;

    public JsonLogEventFormatter() {
    }

    public JsonLogEventFormatter(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public JsonLogEventFormatter(Configuration configuration) {
        configure(configuration);
        configuration.checkForUnknownFields();
    }

    @Override
    public void configure(Configuration configuration) {
        applicationName = configuration.getApplicationName();
        node = configuration.getApplicationNode();
        messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class);
        mdcFilter = configuration.getMdcFilter();
        dateTimeFormatter = configuration
                .optionalString("dateTimeFormat").map(DateTimeFormatter::ofPattern)
                .orElse(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public String apply(LogEvent e) {
        return JsonUtil.toIndentedJson(toJsonObject(e));
    }

    public Map<String, Object> toJsonObject(LogEvent event) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("@timestamp", event.getZonedDateTime().format(dateTimeFormatter));
        payload.put("message", event.getMessage(messageFormatter));
        payload.put("messageFormat", event.getMessage());
        payload.put("thread", event.getThreadName());
        payload.put("level", event.getLevel().toString());
        payload.put("levelInt", event.getLevel().toInt());
        payload.put("logger", event.getLoggerName());
        payload.put("marker", event.getMarker() == null ? null : event.getMarker().getName());
        payload.put("app", applicationName);
        payload.put("hostname", node);
        payload.put("mdc", getMdc(event));

        if (event.getThrowable() != null) {
            payload.put("exception.class", event.getThrowable().getClass().getName());
            payload.put("exception.message", event.getThrowable().getMessage());
            payload.put("exception.stackTrace", exceptionFormatter.format(event.getThrowable()));
        }

        return payload;
    }

    private Map<String, Object> getMdc(LogEvent event) {
        Map<String, Object> mdc = new HashMap<>();

        for (Map.Entry<String, String> mdcEntry : event.getMdcProperties().entrySet()) {
            if (mdcFilter == null || mdcFilter.isKeyIncluded(mdcEntry.getKey())) {
                mdc.put(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }
        return mdc.isEmpty() ? null : mdc;
    }
}
