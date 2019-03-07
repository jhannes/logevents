package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.LogEventFormatterBuilderFactory;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.util.Configuration;
import org.logevents.util.pattern.PatternReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class FileLogEventObserver implements LogEventObserver {

    protected final Path path;
    private final LogEventFormatter filenameGenerator;
    private final LogEventFormatter formatter;
    private final FileDestination destination;

    public static LogEventFormatter createFormatter(Configuration configuration) {
        return configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, TTLLEventLogFormatter.class);
    }

    public static String defaultFilename() {
        Optional<String> filename = Thread.getAllStackTraces().entrySet().stream()
                .filter(pair -> pair.getKey().getName().equals("main"))
                .map(Map.Entry::getValue)
                .findAny()
                .map(stackTrace ->
                    (isRunningInTest(stackTrace))
                            ? "logs/" + currentWorkingDirectory() + "-test.log"
                            : "logs/" + determineJarName(stackTrace[stackTrace.length-1].getClassName()) + "-%date.log"
                );
        return filename.orElse("logs/" + currentWorkingDirectory() + "-%date.log");
    }

    static String determineJarName(String className) {
        try {
            CodeSource codeSource = Class.forName(className).getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return currentWorkingDirectory();
            }
            String path = codeSource.getLocation().getPath();
            if (path.endsWith("/")) {
                return currentWorkingDirectory();
            }
            int lastSlash = path.lastIndexOf('/');
            return path.substring(lastSlash+1)
                    .replaceAll("(-?(\\d+\\.)*\\d+)\\.jar$", "");
        } catch (ClassNotFoundException e) {
            return currentWorkingDirectory();
        }
    }

    static String currentWorkingDirectory() {
        return Paths.get("").toAbsolutePath().getFileName().toString();
    }

    private static boolean isRunningInTest(StackTraceElement[] stackTrace) {
        return Arrays.stream(stackTrace)
                .anyMatch(ste -> ste.getClassName().startsWith("org.junit.runners."));
    }


    public FileLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public FileLogEventObserver(Configuration configuration) {
        this(Optional.of(createFormatter(configuration)), configuration.optionalString("filename"));
        configuration.checkForUnknownFields();
    }

    public FileLogEventObserver(Optional<LogEventFormatter> formatter, Optional<String> path) {
        this.formatter = formatter.orElse(new TTLLEventLogFormatter());
        this.path = Paths.get(path.orElseGet(FileLogEventObserver::defaultFilename));

        this.filenameGenerator = new PatternReader<>(factory).readPattern(this.path.getFileName().toString());
        this.destination = new FileDestination(this.path.getParent());
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        destination.writeEvent(getFilename(logEvent), formatter.apply(logEvent));
    }

    protected String getFilename(LogEvent logEvent) {
        return filenameGenerator.apply(logEvent);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{filename=" + path
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
    }

}
