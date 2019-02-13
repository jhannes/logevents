package org.logevents.observers;

import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

/**
 * Log messages to the system out with suitable formatter.
 * By default, {@link ConsoleLogEventObserver}
 * will log with ANSI colors if supported (on Linux, Mac and when
 * <a href="https://github.com/fusesource/jansi">JANSI</a> is in the classpath on Windows).
 *
 * @author Johannes Brodwall
 */
public class ConsoleLogEventObserver implements LogEventObserver {

    private final LogEventFormatter formatter;
    private final Level threshold;

    public ConsoleLogEventObserver(LogEventFormatter formatter) {
        this.formatter = formatter;
        this.threshold = Level.TRACE;
    }

    public ConsoleLogEventObserver() {
        this(new ConsoleLogEventFormatter());
    }

    public ConsoleLogEventObserver(Configuration configuration) {
        this.formatter = configuration.createInstanceWithDefault("formatter",
                LogEventFormatter.class, ConsoleLogEventFormatter.class);
        this.threshold = configuration.optionalString("threshold").map(Level::valueOf).orElse(Level.TRACE);
        formatter.getExceptionFormatter().ifPresent(
                exceptionFormatter -> exceptionFormatter.setPackageFilter(configuration.getStringList("packageFilter")));
        configuration.checkForUnknownFields();
        LogEventStatus.getInstance().addInfo(this, "Configured " + configuration.getPrefix());
    }

    public ConsoleLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    @Override
    public void logEvent(LogEvent event) {
        if (threshold.toInt() <= event.getLevel().toInt()) {
            System.out.print(formatter.apply(event));
            System.out.flush();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{formatter=" + formatter + "}";
    }
}
