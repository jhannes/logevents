package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
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
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 * @see org.logevents.formatting.PatternLogEventFormatter
 * @see org.logevents.observers.file.FilenameFormatter
 */
public class FileLogEventObserver implements LogEventObserver, AutoCloseable {

    private final FilenameFormatter filenameGenerator;
    private final LogEventFormatter formatter;
    private final FileDestination destination;
    private FileRotationWorker fileRotationWorker;

    public static LogEventFormatter createFormatter(Configuration configuration) {
        LogEventFormatter formatter = configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, TTLLEventLogFormatter.class);
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

    public FileLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public FileLogEventObserver(Configuration configuration) {
        String filenamePattern = configuration.optionalString("filename").orElse(defaultFilename(configuration));
        this.filenameGenerator = new FilenameFormatter(filenamePattern.replaceAll("\\\\", "/"), configuration);
        this.destination = new FileDestination(configuration.getBoolean("lockOnWrite"));

        configuration.optionalString("archivedFilename").ifPresent(archivedFilename -> {
            fileRotationWorker = new FileRotationWorker(filenameGenerator, new FilenameFormatter(archivedFilename, configuration));

            configuration.optionalString("retention").map(Period::parse).ifPresent(fileRotationWorker::setRetention);
            configuration.optionalString("compressAfter").map(Period::parse).ifPresent(fileRotationWorker::setCompressAfter);

            executorService = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("FileLogEventObserver"));
            startFileRotation(fileRotationWorker);
        });

        this.formatter = createFormatter(configuration);
        configuration.checkForUnknownFields();
    }

    public FileLogEventObserver(Configuration configuration, String filenamePattern, Optional<LogEventFormatter> formatter) {
        this.formatter = formatter.orElse(new TTLLEventLogFormatter());
        this.filenameGenerator = new FilenameFormatter(filenamePattern.replaceAll("\\\\", "/"), configuration);
        this.destination = new FileDestination(configuration.getBoolean("lockOnWrite"));
    }

    public FileLogEventObserver(String filenamePattern, LogEventFormatter formatter) {
        this(new Configuration(), filenamePattern, Optional.of(formatter));
    }

    public FileLogEventObserver(FileRotationWorker fileRotationWorker, LogEventFormatter formatter) {
        this.fileRotationWorker = fileRotationWorker;
        this.filenameGenerator = fileRotationWorker.getActiveLogFilenameFormatter();
        this.formatter = formatter;
        this.destination = new FileDestination(false);
        executorService = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("FileLogEventObserver"));
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
    public void logEvent(LogEvent logEvent) {
        destination.writeEvent(getFilename(logEvent), formatter.apply(logEvent));
    }

    protected Path getFilename(LogEvent logEvent) {
        return Paths.get(filenameGenerator.format(logEvent));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{filename=" + filenameGenerator
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
