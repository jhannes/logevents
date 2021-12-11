package org.logevents.formatters;

import org.logevents.LogEvent;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.util.JsonUtil;

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

    public String apply(LogEvent e) {
        return JsonUtil.toCompactJson(toJsonObject(e)) + "\n";
    }
}
