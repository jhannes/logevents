package org.logevents.observers;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.logevents.destinations.FileDestination;
import org.logevents.formatting.PatternLogEventFormatter;

public class FileLogEventObserverTest {

    @Test
    public void shouldCreateFileDestinationWithoutDirectory() throws IOException {
        Path path = Paths.get("test-log-file.log");
        Files.deleteIfExists(path);

        Properties properties = new Properties();
        properties.setProperty("observer.file.destination", FileDestination.class.getSimpleName());
        properties.setProperty("observer.file.destination.filename", path.toString());
        properties.setProperty("observer.file.formatter", PatternLogEventFormatter.class.getSimpleName());
        properties.setProperty("observer.file.formatter.pattern", "%message");
        TextLogEventObserver observer = new TextLogEventObserver(properties, "observer.file");

        assertEquals("TextLogEventObserver{destination=FileDestination{test-log-file.log},formatter=PatternLogEventFormatter{%message}}", observer.toString());
    }

    @Test
    public void shouldLogToFile() throws IOException {
        Path path = Paths.get("target", "test", "log", getClass().getSimpleName() + ".log");
        Files.deleteIfExists(path);

        Properties properties = new Properties();
        properties.setProperty("observer.file.destination", FileDestination.class.getSimpleName());
        properties.setProperty("observer.file.destination.filename", path.toString());
        properties.setProperty("observer.file.formatter", PatternLogEventFormatter.class.getSimpleName());
        properties.setProperty("observer.file.formatter.pattern", "%message");
        TextLogEventObserver observer = new TextLogEventObserver(properties, "observer.file");

        LogEventFactory factory = LogEventFactory.getInstance();
        LoggerConfiguration logger = factory.getLogger(getClass().getName());
        factory.setObserver(logger, observer, false);

        logger.warn("A warning message");

        assertEquals(Arrays.asList("A warning message"), Files.readAllLines(path));
    }

}
