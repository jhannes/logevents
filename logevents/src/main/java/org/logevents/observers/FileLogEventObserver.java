package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.LogEventFormatterBuilderFactory;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.util.pattern.PatternReader;
import org.slf4j.Marker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

/**
 * Logs events to file. By default, FileLogEventObserver will log to the file
 * <code>logs/<em>your-app-name</em>-%date.log</code> as determined by
 * {@link #defaultFilename}. By default, each log event is logged with time,
 * thread, level and logger. Example configuration:
 *
 * <pre>
 * observer.file.filename=logs/my-file-name-%date.log
 * observer.file.formatter=PatternLogEventFormatter
 * observer.file.formatter.pattern=%date %coloredLevel: %msg
 * observer.file.formatter.exceptionFormatter=CauseFirstExceptionFormatter
 * observer.file.formatter.exceptionFormatter.packageFilter=sun.www,uninterestingPackage
 * </pre>
 *
 * <h2>File name pattern</h2>
 *
 * The following conversion words are supported in the filename:
 * <ul>
 *     <li>
 *         <code>%date</code>: The date and time of the log event.
 *         Optionally supports a date formatting pattern from {@link DateTimeFormatter#ofPattern}
 *         e.g. %date{DD-MMM HH:mm:ss}. Default format is <code>yyyy-MM-dd HH:mm:ss.SSS</code>.
 *     </li>
 *     <li><code>%marker</code>: {@link Marker} (if any) (not implemented yet)</li>
 *     <li>
 *         <code>%mdc{key:-default}</code>:
 *         the specified {@link org.slf4j.MDC} variable or default value if not set
 *     </li>
 *     <li><code>%application</code>: The value of {@link Configuration#getApplicationName()} (not implemented yet)</li>
 *     <li><code>%node</code>: The value of {@link Configuration#getNodeName()} ()} (not implemented yet)</li>
 * </ul>
 *
 * @see org.logevents.formatting.PatternLogEventFormatter
 */
public class FileLogEventObserver implements LogEventObserver {

    private final LogEventFormatter filenameGenerator;
    private final LogEventFormatter formatter;
    private String filenamePattern;
    private final FileDestination destination;

    public static LogEventFormatter createFormatter(Configuration configuration) {
        return configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, TTLLEventLogFormatter.class);
    }

    /**
     * Determines default log file name. If the main class of the application
     * was loaded from a jar-file, the name of the jar-file, without version number,
     * will be used. If the main class of the application was loaded from a directory,
     * the current working directory base name will be used.
     */
    public static String defaultFilename(Configuration configuration) {
        if (Configuration.isRunningInTest()) {
            return "logs/" + configuration.getApplicationName() + "-test.log";
        } else {
            return "logs/" + configuration.getApplicationName() + "-%date.log";
        }
    }

    public FileLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public FileLogEventObserver(Configuration configuration) {
        this(
                configuration,
                Optional.of(createFormatter(configuration)),
                configuration.optionalString("filename").orElse(defaultFilename(configuration))
        );
        formatter.configure(configuration);
        configuration.checkForUnknownFields();
    }

    public FileLogEventObserver(Configuration configuration, Optional<LogEventFormatter> formatter, String filenamePattern) {
        this.formatter = formatter.orElse(new TTLLEventLogFormatter());
        this.filenamePattern = filenamePattern;

        this.filenameGenerator = new PatternReader<>(configuration, factory).readPattern(filenamePattern);
        this.destination = new FileDestination();
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        destination.writeEvent(getFilename(logEvent), formatter.apply(logEvent));
    }

    protected Path getFilename(LogEvent logEvent) {
        return Paths.get(filenameGenerator.apply(logEvent));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{filename=" + filenamePattern
                + ",formatter=" + formatter + "}";
    }


    private static LogEventFormatterBuilderFactory factory = new LogEventFormatterBuilderFactory();

    static {
        factory.put("date", spec -> {
            DateTimeFormatter formatter = spec.getParameter(0)
                    .map(DateTimeFormatter::ofPattern)
                    .orElse(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            ZoneId zone = spec.getParameter(1)
                    .map(ZoneId::of)
                    .orElse(ZoneId.systemDefault());
            return e -> formatter.format(Instant.now().atZone(zone));
        });

        factory.put("d", factory.get("date"));
        factory.put("mdc", spec -> {
            if (spec.getParameters().isEmpty()) {
                return LogEvent::getMdc;
            } else {
                String[] parts = spec.getParameters().get(0).split(":-");
                String key = parts[0];
                String defaultValue = parts.length > 1 ? parts[1] : "";
                return e -> e.getMdc(key, defaultValue);
            }
        });
        factory.putAliases("mdc", new String[] { "X" });

    }

}
