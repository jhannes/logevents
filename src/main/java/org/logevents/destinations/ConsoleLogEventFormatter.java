package org.logevents.destinations;

import org.logevents.LogEvent;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

/**
 * A simple formatter used by {@link ConsoleLogEventObserver} by default.
 * Suitable for overriding {@link #format(LogEvent)}
 *
 * @author Johannes Brodwall
 *
 */
public class ConsoleLogEventFormatter implements LogEventFormatter {

    protected final ConsoleFormatting format = ConsoleFormatting.getInstance();

    @Override
    public String format(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]: %s\n",
                e.getZonedDateTime().toLocalTime(),
                e.getThreadName(),
                colorizedLevel(e),
                format.bold(e.getLoggerName()),
                e.formatMessage())
                + e.formatStackTrace();
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
        String levelString = LogEventFormatter.rightPad(level, 5, ' ');
        if (level == Level.ERROR) {
            return format.boldRed(levelString);
        } else if (level == Level.WARN) {
            return format.red(levelString);
        } else if (level == Level.INFO) {
            return format.blue(levelString);
        }
        return levelString;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
