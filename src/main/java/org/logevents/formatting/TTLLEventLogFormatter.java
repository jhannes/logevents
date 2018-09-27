package org.logevents.formatting;

import org.logevents.LogEvent;

/**
 * A simple and convenient {@link LogEventFormatter} which outputs
 * Time, Thread, Level, Logger as well as the log message and stack trace.
 *
 * @author Johannes Brodwall
 */
public final class TTLLEventLogFormatter implements LogEventFormatter {

    protected final ExceptionFormatter exceptionFormatter = new ExceptionFormatter();

    @Override
    public String apply(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]: %s",
                e.getZonedDateTime().toLocalTime(),
                e.getThreadName(),
                LogEventFormatter.rightPad(e.getLevel(), 5, ' '),
                e.getLoggerName(),
                e.formatMessage())
                + exceptionFormatter.format(e.getThrowable());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}