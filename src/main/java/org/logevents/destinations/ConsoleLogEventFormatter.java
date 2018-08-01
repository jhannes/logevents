package org.logevents.destinations;

import org.logevents.LogEvent;
import org.slf4j.event.Level;

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

    protected String colorizedLevel(LogEvent e) {
        return colorizedLevel(e.getLevel());
    }

    protected String colorizedLevel(Level level) {
        String levelString = LogEventFormatter.rightPad(level, 5, ' ');
        if (level == Level.ERROR) {
            return format.red(levelString);
        } else if (level == Level.WARN) {
            return format.yellow(levelString);
        } else if (level == Level.INFO) {
            return format.green(levelString);
        }
        return format.green(levelString);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
