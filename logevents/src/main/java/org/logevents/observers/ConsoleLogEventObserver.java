package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.formatting.LogEventFormatter;

import java.io.PrintStream;
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
 * observer.console.outputToSyserr=true
 * observer.console.includedMdcKeys=clientIp
 * observer.console.suppressMarkers=UNINTERESTING, PERSONAL_DATA
 * observer.console.packageFilter=sun.www, com.example.uninteresting
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ConsoleLogEventObserver extends FilteredLogEventObserver {

    private final LogEventFormatter formatter;
    private PrintStream out;

    public ConsoleLogEventObserver(LogEventFormatter formatter) {
        this.formatter = formatter;
        out = System.out;
    }

    public ConsoleLogEventObserver() {
        this(new ConsoleLogEventFormatter());
    }

    public ConsoleLogEventObserver(Configuration configuration) {
        this.formatter = configuration.createInstanceWithDefault("formatter",
                LogEventFormatter.class, ConsoleLogEventFormatter.class);
        configureFilter(configuration);
        formatter.configure(configuration);
        boolean outputToSyserr = configuration.getBoolean("outputToSyserr");
        out = outputToSyserr ? System.err : System.out;
        configuration.checkForUnknownFields();
    }

    public ConsoleLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public LogEventFormatter getFormatter() {
        return formatter;
    }


    @Override
    protected void doLogEvent(LogEvent logEvent) {
        out.print(formatter.apply(logEvent));
        out.flush();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{formatter=" + formatter + "}";
    }
}
