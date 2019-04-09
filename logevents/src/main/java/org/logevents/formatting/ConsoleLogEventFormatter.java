package org.logevents.formatting;

import org.logevents.LogEvent;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * A simple formatter used by {@link ConsoleLogEventObserver} by default.
 * Suitable for overriding {@link #apply(LogEvent)}
 *
 * @author Johannes Brodwall
 *
 */
public class ConsoleLogEventFormatter implements LogEventFormatter {

    protected final ConsoleFormatting format = ConsoleFormatting.getInstance();

    protected final ExceptionFormatter exceptionFormatter = new ExceptionFormatter();
    private DateTimeFormatter timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private String[] includeMdcKeys = new String[0];

    @Override
    public Optional<ExceptionFormatter> getExceptionFormatter() {
        return Optional.of(exceptionFormatter);
    }

    @Override
    public String apply(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]%s: %s\n",
                e.getZonedDateTime().format(timeOnlyFormatter),
                e.getThreadName(),
                colorizedLevel(e),
                format.bold(e.getLoggerName()),
                mdc(e, includeMdcKeys),
                formatMessage(e))
                + exceptionFormatter.format(e.getThrowable());
    }

    /**
     * See {@link #colorizedLevel(Level)}
     */
    protected String colorizedLevel(LogEvent e) {
        return colorizedLevel(e.getLevel());
    }

    /**
     * Output ANSI color coded level string, where ERROR is bold red, WARN is
     * red, INFO is blue and other levels are default color.
     */
    protected String colorizedLevel(Level level) {
        return format.highlight(level, LogEventFormatter.rightPad(level, 5, ' '));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public void configure(Configuration configuration) {
        getExceptionFormatter().ifPresent(
                exceptionFormatter -> exceptionFormatter.setPackageFilter(configuration.getStringList("packageFilter")));
        includeMdcKeys = configuration.getStringList("includeMdcKeys");
    }
}
