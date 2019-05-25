package org.logevents.formatting;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;

/**
 * A simple and convenient {@link LogEventFormatter} which outputs
 * Time, Thread, Level, Logger as well as the log message and stack trace.
 *
 * @author Johannes Brodwall
 */
public final class TTLLEventLogFormatter implements LogEventFormatter {

    protected MessageFormatter messageFormatter = new MessageFormatter();
    protected final ExceptionFormatter exceptionFormatter = new ExceptionFormatter();

    private String[] includeMdcKeys = new String[0];

    @Override
    public String apply(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]%s: %s\n",
                e.getLocalTime(),
                e.getThreadName(),
                LogEventFormatter.rightPad(e.getLevel(), 5, ' '),
                e.getLoggerName(),
                mdc(e, includeMdcKeys),
                messageFormatter.format(e.getMessage(), e.getArgumentArray()))
                + exceptionFormatter.format(e.getThrowable());
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