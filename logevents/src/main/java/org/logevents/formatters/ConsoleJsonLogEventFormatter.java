package org.logevents.formatters;

import org.logevents.LogEvent;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.util.JsonUtil;

import java.util.Map;

/**
 * A JSON formatter for use with {@link ConsoleLogEventObserver}.
 * Suitable for overriding redirecting standard out to
 * <a href="https://www.elastic.co/logstash">Logstash</a> or similar.
 *
 * Example configuration
 *
 * <pre>
 * observer.console.formatter=ConsoleJsonLogEventFormatter
 * observer.console.excludedMdcKeys=secret,ipAddress
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ConsoleJsonLogEventFormatter extends JsonLogEventFormatter {

    public ConsoleJsonLogEventFormatter(Map<String, String> properties, String prefix) {
        super(properties, prefix);
    }

    public String apply(LogEvent e) {
        return JsonUtil.toCompactJson(toJsonObject(e)) + "\n";
    }
}
