package org.logevents.formatters;

import org.logevents.LogEvent;
import org.logevents.LogEventFormatter;
import org.logevents.config.Configuration;
import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.util.StringUtil;

/**
 * A simple and convenient {@link LogEventFormatter} which outputs
 * Time, Thread, Level, Logger as well as the log message and stack trace.
 * This is equivalent to:
 *
 * <pre>
 * observer...formatter=PatternLogEventFormatter
 * observer...formatter.pattern=%time [%thread] [%5level] [%logger]%mdc: %message
 * </pre>
 *
 * @author Johannes Brodwall
 */
public final class TTLLLogEventFormatter implements LogEventFormatter {

    private final MessageFormatter messageFormatter = new MessageFormatter();
    private final ExceptionFormatter exceptionFormatter = new ExceptionFormatter();

    private MdcFilter mdcFilter = MdcFilter.INCLUDE_ALL;
    private boolean showMarkers;

    @Override
    public String apply(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]%s%s: %s\n",
                e.getLocalTime(),
                e.getThreadName(),
                StringUtil.rightPad(e.getLevel(), 5, ' '),
                e.getLoggerName(),
                showMarkers && e.getMarker() != null ? " {" + e.getMarker() + "}" : "",
                e.getMdcString(mdcFilter),
                e.getMessage(messageFormatter)
        ) + exceptionFormatter.format(e.getThrowable());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public void configure(Configuration configuration) {
        getExceptionFormatter().ifPresent(
                exceptionFormatter -> exceptionFormatter.setPackageFilter(configuration.getPackageFilter()));
        mdcFilter = configuration.getMdcFilter();
        showMarkers = configuration.getBoolean("showMarkers");
    }

}
