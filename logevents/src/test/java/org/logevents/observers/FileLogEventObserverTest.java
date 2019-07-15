package org.logevents.observers;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LoggerConfiguration;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.formatting.PatternLogEventFormatter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileLogEventObserverTest {

    private static final String CWD = Paths.get("").toAbsolutePath().getFileName().toString();

    private LogEventFactory factory = new LogEventFactory();
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
    public void shouldCreateDefaultFilename() {
        Properties properties = new Properties();
        FileLogEventObserver observer = new FileLogEventObserver(properties, "observer.file");

        assertEquals(CWD + "-test.log", observer.getFilename(new LogEventSampler().build()));
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
    public void shouldCreateDirectoryAtFirstLogEvent() throws IOException {
        Path path = Paths.get("target", "test", "creation-test", "log-" + System.currentTimeMillis(), getClass().getSimpleName() + ".log");
        Files.deleteIfExists(path);
        Files.deleteIfExists(path.getParent());

        Properties properties = new Properties();
        properties.setProperty("observer.file.filename", path.toString());
        LogEventObserver observer = new FileLogEventObserver(properties, "observer.file");
        factory.setObserver(logger, observer, false);

        assertFalse(Files.exists(path.getParent()));

        logger.warn("A warning message");
        assertTrue(Files.exists(path.getParent()));
    }

    @Test
    public void shouldLogToFileWithPattern() throws IOException {
        Path logDirectory = Paths.get("target", "test", "file-test-log");
        if (Files.exists(logDirectory)) {
            Files.walk(logDirectory).map(Path::toFile).forEach(File::delete);
        }

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
