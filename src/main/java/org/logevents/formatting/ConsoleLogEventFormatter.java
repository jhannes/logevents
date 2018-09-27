package org.logevents.formatting;

import org.logevents.LogEvent;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

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

    @Override
    public String apply(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]: %s",
                e.getZonedDateTime().toLocalTime(),
                e.getThreadName(),
                colorizedLevel(e),
                format.bold(e.getLoggerName()),
                e.formatMessage())
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
}
