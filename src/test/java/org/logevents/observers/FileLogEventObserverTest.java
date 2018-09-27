package org.logevents.observers;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LoggerConfiguration;
import org.logevents.formatting.PatternLogEventFormatter;

public class FileLogEventObserverTest {

    private LogEventFactory factory = LogEventFactory.getInstance();
    private LoggerConfiguration logger = factory.getLogger(getClass().getName());

    @Test
    public void shouldCreateFileDestinationWithoutDirectory() throws IOException {
        Path path = Paths.get("test-log-file.log");
        Files.deleteIfExists(path);

        Properties properties = new Properties();
        properties.setProperty("observer.file.filename", path.toString());
        properties.setProperty("observer.file.formatter", PatternLogEventFormatter.class.getSimpleName());
        properties.setProperty("observer.file.formatter.pattern", "%message");
        LogEventObserver observer = new FileLogEventObserver(properties, "observer.file");

        assertEquals("FileLogEventObserver{filename=test-log-file.log,formatter=PatternLogEventFormatter{%message}}", observer.toString());
    }

    @Test
    public void shouldLogToFile() throws IOException {
        Path path = Paths.get("target", "test", "log", getClass().getSimpleName() + ".log");
        Files.deleteIfExists(path);

        Properties properties = new Properties();
        properties.setProperty("observer.file.filename", path.toString());
        properties.setProperty("observer.file.formatter", PatternLogEventFormatter.class.getSimpleName());
        properties.setProperty("observer.file.formatter.pattern", "%message");
        LogEventObserver observer = new FileLogEventObserver(properties, "observer.file");

        factory.setObserver(logger, observer, false);

        logger.warn("A warning message");

        assertEquals(Arrays.asList("A warning message"), Files.readAllLines(path));
    }

    @Test
    public void shouldLogToFileWithPattern() throws IOException {
        Path logDirectory = Paths.get("target", "test", "log");
        Files.walk(logDirectory).map(Path::toFile).forEach(File::delete);

        Properties properties = new Properties();
        properties.setProperty("observer.file.filename", logDirectory.toString() + "/mylog-%date.log");
        properties.setProperty("observer.file.formatter", PatternLogEventFormatter.class.getSimpleName());
        properties.setProperty("observer.file.formatter.pattern", "%message");
        LogEventObserver observer = new FileLogEventObserver(properties, "observer.file");

        factory.setObserver(logger, observer, false);
        logger.warn("A warning message");

        Path path = logDirectory.resolve("mylog-" + LocalDate.now() + ".log");

        assertEquals(Arrays.asList(path),
                Files.walk(logDirectory).filter(Files::isRegularFile).collect(Collectors.toList()));
        assertEquals(Arrays.asList("A warning message"), Files.readAllLines(path));
    }

}
