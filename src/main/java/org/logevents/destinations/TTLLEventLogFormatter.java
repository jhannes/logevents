package org.logevents.destinations;

import org.logevents.LogEvent;

public final class TTLLEventLogFormatter implements LogEventFormatter {
    @Override
    public String format(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]: %s\n",
                e.getZonedDateTime().toLocalTime(),
                e.getThreadName(),
                LogEventFormatter.rightPad(e.getLevel(), 5, ' '),
                e.getLoggerName(),
                e.formatMessage())
                + e.formatStackTrace();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}