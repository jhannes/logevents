package org.logevents.formatting;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * A simple formatter used by {@link ConsoleLogEventObserver} by default.
 * Suitable for overriding {@link #apply(LogEvent)}
 *
 * This is equivalent to
 * <pre>
 * observer...formatter=PatternLogEventFormatter
 * observer...formatter.pattern=%time [%thread] [%coloredLevel] [%bold(%location)]%mdc: %message
 * </pre>
 *
 * Example configuration
 *
 * <pre>
 * observer.console.includedMdcKeys=clientIp
 * observer.*.packageFilter=sun.www, com.example.uninteresting
 * observer.console.formatter.color=true
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ConsoleLogEventFormatter implements LogEventFormatter {

    protected ConsoleFormatting format = ConsoleFormatting.getInstance();
    protected MessageFormatter messageFormatter = new ConsoleMessageFormatter(format);
    protected final ExceptionFormatter exceptionFormatter = new ExceptionFormatter();
    protected final DateTimeFormatter timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    protected MdcFilter mdcFilter;

    @Override
    public Optional<ExceptionFormatter> getExceptionFormatter() {
        return Optional.of(exceptionFormatter);
    }

    @Override
    public String apply(LogEvent e) {
        // TODO: MARKER
        return String.format("%s [%s] [%s] [%s]%s: %s\n",
                e.getZonedDateTime().format(timeOnlyFormatter),
                e.getThreadName(),
                colorizedLevel(e),
                format.bold(e.getSimpleCallerLocation()),
                mdc(e, mdcFilter),
                e.getMessage(messageFormatter))
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
    public String colorizedLevel(Level level) {
        return format.highlight(level, LogEventFormatter.rightPad(level, 5, ' '));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public void configure(Configuration configuration) {
        getExceptionFormatter().ifPresent(exceptionFormatter ->
                exceptionFormatter.setPackageFilter(configuration.getPackageFilter())
        );
        mdcFilter = configuration.getMdcFilter();

        if (configuration.optionalString("color").isPresent()) {
            format = configuration.getBoolean("color") ? ConsoleFormatting.ANSI_FORMATTING : ConsoleFormatting.NULL_FORMATTING;
            messageFormatter = new ConsoleMessageFormatter(format);
        }
    }

}
