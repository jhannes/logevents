package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventFormatter;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.core.AbstractFilteredLogEventObserver;
import org.logevents.formatters.TTLLLogEventFormatter;
import org.logevents.observers.file.FileDestination;
import org.logevents.observers.file.FileRotationWorker;
import org.logevents.observers.file.FilenameFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.DaemonThreadFactory;
import org.slf4j.Marker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.event.Level;

/**
 * Logs events to file. By default, FileLogEventObserver will log to the file
 * <code>logs/<em>your-app-name</em>-%date.log</code> as determined by
 * {@link #defaultFilename}. By default, each log event is logged with time,
 * thread, level and logger. Uses {@link FileRotationWorker} to archive and delete
 * old logs.
 *
 * <h3>Example configuration:</h3>
 *
 * <pre>
 * observer.file.filename=logs/%application.log
 * observer.file.archivedFilename=logs/%date{%yyyy-MM}/%application-%date.log
 * observer.file.retention=P4W
 * observer.file.compressAfter=P1W
 * observer.file.formatter=PatternLogEventFormatter
 * observer.file.formatter.pattern=%date %coloredLevel: %msg
 * observer.file.formatter.exceptionFormatter=CauseFirstExceptionFormatter
 * observer.file.formatter.exceptionFormatter.packageFilter=sun.www,uninterestingPackage
 * observer.file.threshold=WARN
 * </pre>
 *
 * <h3>File name pattern</h3>
 *
 * The following conversion words are supported in the filename:
 * <ul>
 *     <li>
 *         <code>%date</code>: The date and time of the log event.
 *         Optionally supports a date formatting pattern from {@link DateTimeFormatter#ofPattern}
 *         e.g. %date{DD-MMM HH:mm:ss}. Default format is <code>yyyy-MM-dd HH:mm:ss.SSS</code>.
 *     </li>
 *     <li><code>%marker[{defaultValue}]</code>: {@link Marker} (if any)</li>
 *     <li>
 *         <code>%mdc{key:-default}</code>:
 *         the specified {@link org.slf4j.MDC} variable or default value if not set
 *     </li>
 *     <li><code>%application</code>: The value of {@link Configuration#getApplicationName()}</li>
 *     <li><code>%node</code>: The value of {@link Configuration#getNodeName()} ()}</li>
 * </ul>
 *
 * @see org.logevents.formatters.PatternLogEventFormatter
 * @see org.logevents.observers.file.FilenameFormatter
 */
public class FileLogEventObserver extends AbstractFilteredLogEventObserver implements AutoCloseable {

    private final FilenameFormatter filenameFormatter;
    private final LogEventFormatter formatter;
    private final FileDestination destination;
    private FileRotationWorker fileRotationWorker;

    public static LogEventFormatter createFormatter(Configuration configuration) {
        LogEventFormatter formatter = configuration.createFormatter("formatter", TTLLLogEventFormatter.class);
        formatter.configure(configuration);
        return formatter;
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

    public FileLogEventObserver(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public FileLogEventObserver(Configuration configuration) {
        String filenamePattern = configuration.optionalString("filename").orElse(defaultFilename(configuration));
        this.filenameFormatter = new FilenameFormatter(filenamePattern.replaceAll("\\\\", "/"), configuration);
        this.destination = new FileDestination(configuration.getBoolean("lockOnWrite"));

        configuration.optionalString("archivedFilename").ifPresent(archivedFilename -> {
            fileRotationWorker = new FileRotationWorker(filenameFormatter, new FilenameFormatter(archivedFilename, configuration));

            configuration.optionalString("retention").map(Period::parse).ifPresent(fileRotationWorker::setRetention);
            configuration.optionalString("compressAfter").map(Period::parse).ifPresent(fileRotationWorker::setCompressAfter);

            executorService = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("FileLogEventObserver", 3));
            startFileRotation(fileRotationWorker);
        });
        this.configureFilter(configuration, Level.TRACE);
        this.formatter = createFormatter(configuration);
        configuration.checkForUnknownFields();
    }

    public FileLogEventObserver(Configuration configuration, String filenamePattern, Optional<LogEventFormatter> formatter) {
        this.formatter = formatter.orElse(new TTLLLogEventFormatter());
        this.filenameFormatter = new FilenameFormatter(filenamePattern.replaceAll("\\\\", "/"), configuration);
        this.destination = new FileDestination(configuration.getBoolean("lockOnWrite"));
    }

    public FileLogEventObserver(String filenamePattern, LogEventFormatter formatter) {
        this(new Configuration(), filenamePattern, Optional.of(formatter));
    }

    public FileLogEventObserver(FileRotationWorker fileRotationWorker, LogEventFormatter formatter) {
        this.fileRotationWorker = fileRotationWorker;
        this.filenameFormatter = fileRotationWorker.getActiveLogFilenameFormatter();
        this.formatter = formatter;
        this.destination = new FileDestination(false);
        executorService = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("FileLogEventObserver", 3));
        startFileRotation(fileRotationWorker);
    }

    private ScheduledExecutorService executorService;

    private void startFileRotation(FileRotationWorker fileRotationWorker) {
        fileRotationWorker.rollover();
        destination.reset();
        long delay = fileRotationWorker.nextExecution().toInstant().toEpochMilli() - Instant.now().toEpochMilli();
        executorService.schedule(() -> startFileRotation(fileRotationWorker), delay, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doLogEvent(LogEvent logEvent) {
        destination.writeEvent(getFilename(logEvent), formatter.apply(logEvent));
    }

    protected Path getFilename(LogEvent logEvent) {
        return Paths.get(filenameFormatter.format(logEvent));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
               + "{filename=" + filenameFormatter
               + ",formatter=" + formatter
               + ",fileRotationWorker=" + fileRotationWorker + "}";
    }

    @Override
    public void close() {
        shutdown();
    }

    List<Runnable> shutdown() {
        if (executorService != null) {
            LogEventStatus.getInstance().addDebug(this, "Shutdown " + executorService);
            return executorService.shutdownNow();
        }
        return new ArrayList<>();
    }
}
