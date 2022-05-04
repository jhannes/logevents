package org.logevents.formatters;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.config.MdcFilter;
import org.logevents.LogEventFormatter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.formatters.messages.ConsoleMessageFormatter;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.util.StringUtil;
import org.slf4j.event.Level;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * observer.*.packageFilter=sun.www, com.example.uninteresting
 * observer.console.includedMdcKeys=clientIp
 * observer.console.showMarkers=true
 * observer.console.color=true
 * observer.console.logFilenameForPackages=com.example.myapp
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
    private boolean showMarkers;
    private List<String> logFilenameForPackages = new ArrayList<>();

    @Override
    public Optional<ExceptionFormatter> getExceptionFormatter() {
        return Optional.of(exceptionFormatter);
    }

    @Override
    public String apply(LogEvent e) {
        // TODO: MARKER
        return String.format("%s [%s] [%s] [%s]%s%s: %s\n",
                e.getZonedDateTime().format(timeOnlyFormatter),
                e.getThreadName(),
                colorizedLevel(e),
                format.bold(logger(e)),
                showMarkers && e.getMarker() != null ? " {" + e.getMarker() + "}" : "",
                e.getMdcString(mdcFilter),
                e.getMessage(messageFormatter))
                + exceptionFormatter.format(e.getThrowable());
    }

    private String logger(LogEvent e) {
        if (logFilenameForPackages.isEmpty()) {
            return e.getLoggerName();
        }
        String className = e.getCallerLocation().getClassName();
        if (logFilenameForPackages.stream().anyMatch(className::startsWith)) {
            return e.getSimpleCallerLocation();
        }
        return e.getLoggerName();
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
        return format.highlight(level, StringUtil.rightPad(level, 5, ' '));
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
        showMarkers = configuration.getBoolean("showMarkers");
        if (configuration.containsKey("logFilenameForPackages")) {
            logFilenameForPackages = configuration.getStringList("logFilenameForPackages");
        } else {
            logFilenameForPackages = Configuration.getMainClassName().map(c -> {
                int lastDotPos = c.lastIndexOf(".");
                return lastDotPos != -1 ? c.substring(0, lastDotPos) : c;
            }).map(Arrays::asList).orElse(new ArrayList<>());
        }

        if (configuration.optionalString("color").isPresent()) {
            format = configuration.getBoolean("color") ? ConsoleFormatting.ANSI_FORMATTING : ConsoleFormatting.NULL_FORMATTING;
            messageFormatter = new ConsoleMessageFormatter(format);
        }
    }

}
