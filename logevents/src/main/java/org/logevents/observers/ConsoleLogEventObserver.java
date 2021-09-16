package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.PatternLogEventFormatter;
import org.slf4j.event.Level;

import java.io.PrintStream;
import java.util.Properties;

/**
 * Log messages to the system out with suitable formatter.
 * Ansi colors will be used if running on a non-Windows shell or if
 * <a href="https://github.com/fusesource/jansi">JANSI</a> is in class path.
 * (Color on Windows is supported in IntelliJ, Cygwin and Ubuntu for Windows).
 *
 * <p>Example configuration (not usually needed, default configuration should serve most purposes)
 *
 * <pre>
 * observer.console.threshold=WARN
 * observer.console.outputToSyserr=true
 * observer.console.includedMdcKeys=clientIp
 * observer.console.suppressMarkers=UNINTERESTING, PERSONAL_DATA
 * observer.console.packageFilter=sun.www, com.example.uninteresting
 * </pre>
 *
 * To add custom pattern format to the observer (shorthand for ...formatter=PatternLogEventFormatter
 * and ...formatter.pattern=...):
 * 
 * <pre>
 * observer.console.pattern=%time [%thread] [%coloredLevel] [%bold(%location)]%mdc: %message
 * </pre>
 * 
 * To override ANSI formatting in {@link ConsoleLogEventFormatter}, use:
 *
 * <pre>
 * observer.console.formatter.colors=false
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ConsoleLogEventObserver extends AbstractFilteredLogEventObserver {

    private final LogEventFormatter formatter;
    private final PrintStream out;

    public ConsoleLogEventObserver(LogEventFormatter formatter, PrintStream out) {
        this.formatter = formatter;
        this.out = out;
    }

    public ConsoleLogEventObserver(LogEventFormatter formatter) {
        this(formatter, System.out);
    }

    public ConsoleLogEventObserver() {
        this(new ConsoleLogEventFormatter());
    }

    public ConsoleLogEventObserver(Configuration configuration) {
        this(createFormatter(configuration), configuration.getBoolean("outputToSyserr") ? System.err : System.out);
        configureFilter(configuration, Level.TRACE);
        formatter.configure(configuration);
        configuration.checkForUnknownFields();
    }

    private static LogEventFormatter createFormatter(Configuration configuration) {
        if (configuration.optionalString("pattern").isPresent()) {
            return new PatternLogEventFormatter(configuration);
        }
        return configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, ConsoleLogEventFormatter.class);
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
