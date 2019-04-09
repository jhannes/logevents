package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;

import java.util.Properties;

/**
 * Log messages to the system out with suitable formatter.
 * Ansi colors will be used if running on a non-Windows shell or if
 * <a href="https://github.com/fusesource/jansi">JANSI</a> is in class path.
 * (Color on Windows is supported in IntelliJ, Cygwin and Ubuntu for Windows).
 * <p>
 * Example configuration
 *
 * <pre>
 * observer.console.threshold=WARN
 * observer.suppressMarkers=UNINTERESTING, PERSONAL_DATA
 * observer.packageFilter=sun.www, com.example.uninteresting
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ConsoleLogEventObserver extends FilteredLogEventObserver {

    private final LogEventFormatter formatter;

    public ConsoleLogEventObserver(LogEventFormatter formatter) {
        this.formatter = formatter;
    }

    public ConsoleLogEventObserver() {
        this(new ConsoleLogEventFormatter());
    }

    public ConsoleLogEventObserver(Configuration configuration) {
        this.formatter = configuration.createInstanceWithDefault("formatter",
                LogEventFormatter.class, ConsoleLogEventFormatter.class);
        configureFilter(configuration);
        formatter.configure(configuration);
        configuration.checkForUnknownFields();
        LogEventStatus.getInstance().addInfo(this, "Configured " + configuration.getPrefix());
    }

    public ConsoleLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public LogEventFormatter getFormatter() {
        return formatter;
    }


    @Override
    protected void doLogEvent(LogEvent logEvent) {
        System.out.print(formatter.apply(logEvent));
        System.out.flush();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{formatter=" + formatter + "}";
    }
}
