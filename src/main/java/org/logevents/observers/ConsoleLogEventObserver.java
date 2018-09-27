package org.logevents.observers;

import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;

/**
 * Log messages to the system out with suitable formatter.
 * By default, {@link ConsoleLogEventObserver}
 * will log with ANSI colors if supported (on Linux, Mac and when
 * <a href="https://github.com/fusesource/jansi">JANSI</a> is in the classpath on Windows).
 *
 * @author Johannes Brodwall
 */
public class ConsoleLogEventObserver implements LogEventObserver {

    private LogEventFormatter formatter;

    public ConsoleLogEventObserver(LogEventFormatter formatter) {
        this.formatter = formatter;
    }

    public ConsoleLogEventObserver() {
        this(new ConsoleLogEventFormatter());
    }

    public ConsoleLogEventObserver(Configuration configuration) {
        this(configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, ConsoleLogEventFormatter.class));
        LogEventStatus.getInstance().addInfo(this, "Configured " + configuration.getPrefix());
    }

    public ConsoleLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        System.out.print(formatter.apply(logEvent));
        System.out.flush();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{formatter=" + formatter + "}";
    }
}
