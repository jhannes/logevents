package org.logevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.After;
import org.junit.Test;
import org.slf4j.event.Level;

public class DefaultLogEventConfiguratorTest {

    private LogEventFactory factory = new LogEventFactory();
    private DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
    private Properties properties = new Properties();
    private Path propertiesDir = Paths.get("target", "test-data", "properties");

    @Test
    public void shouldSetRootLevelFromProperties() {
        properties.setProperty("root", "TRACE");
        String oldObserver = factory.getRootLogger().getObserver();

        configurator.loadConfiguration(factory, properties);

        assertTrue(factory.getLoggers() + " should be empty", factory.getLoggers().isEmpty());
        assertEquals(Level.TRACE, factory.getRootLogger().getLevelThreshold());
        assertEquals(oldObserver, factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetRootObserverFromProperties() {
        Path logFile = Paths.get("logs", "application.log");
        properties.setProperty("root", "DEBUG file");
        properties.setProperty("observer.file", "DateRollingLogEventObserver");
        properties.setProperty("observer.file.filename", logFile.toString());

        configurator.loadConfiguration(factory, properties);
        assertEquals(Level.DEBUG, factory.getRootLogger().getLevelThreshold());
        assertEquals(
                "DateRollingLogEventObserver{"
                + "filename=" + logFile + ","
                + "formatter=TTLLEventLogFormatter}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetLoggerObserverFromProperties() {
        properties.setProperty("logger.org.example", "ERROR buffer1,buffer2");
        properties.setProperty("observer.buffer1", "CircularBufferLogEventObserver");
        properties.setProperty("observer.buffer2", "CircularBufferLogEventObserver");

        configurator.loadConfiguration(factory, properties);
        assertEquals(Level.ERROR, factory.getLogger("org.example").getLevelThreshold());
        assertEquals(
                "CompositeLogEventObserver{"
                + "[CircularBufferLogEventObserver{size=0}, CircularBufferLogEventObserver{size=0}]}",
                factory.getLogger("org.example").getObserver());
    }

    @Test
    public void shouldScanPropertiesFilesWhenFileIsChanged() throws IOException, InterruptedException {
        propertiesDir = Paths.get("target", "test-data", "scan-change", "properties");
        deleteConfigFiles();
        Files.createDirectories(propertiesDir);

        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("root", "DEBUG");
        defaultProperties.setProperty("observer.console", "ConsoleLogEventObserver");
        defaultProperties.setProperty("observer.null", "NullLogEventObserver");
        writeProps(propertiesDir.resolve("logevents.properties"), defaultProperties);

        Properties firstProfileProperty = new Properties();
        firstProfileProperty.setProperty("root", "ERROR console");
        writeProps(propertiesDir.resolve("logevents-profile1.properties"), firstProfileProperty);

        System.setProperty("profiles", "profile1");
        DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator(propertiesDir);
        LogEventFactory logEventFactory = new LogEventFactory();
        configurator.configure(logEventFactory);

        assertEquals("ERROR", logEventFactory.getRootLogger().getLevelThreshold().toString());
        assertEquals("ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}", logEventFactory.getRootLogger().getObserver());

        firstProfileProperty.setProperty("root", "TRACE null");
        writeProps(propertiesDir.resolve("logevents-profile1.properties"), firstProfileProperty);

        Thread.sleep(10);

        assertEquals("TRACE", logEventFactory.getRootLogger().getLevelThreshold().toString());
        assertEquals("<inherit>", logEventFactory.getRootLogger().getObserver());
    }

    @Test
    public void shouldScanPropertiesFilesWhenHigherPriorityFileIsAdded() throws IOException, InterruptedException {
        propertiesDir = Paths.get("target", "test-data", "scan-new", "properties");
        deleteConfigFiles();
        Files.createDirectories(propertiesDir);

        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("root", "DEBUG");
        writeProps(propertiesDir.resolve("logevents.properties"), defaultProperties);

        System.setProperty("profiles", "production");
        DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator(propertiesDir);
        LogEventFactory logEventFactory = new LogEventFactory();
        configurator.configure(logEventFactory);

        assertEquals("DEBUG", logEventFactory.getRootLogger().getLevelThreshold().toString());

        Properties newPropertiesFile = new Properties();
        Thread.sleep(10);
        newPropertiesFile.setProperty("root", "INFO");
        writeProps(propertiesDir.resolve("logevents-production.properties"), newPropertiesFile);
        assertEquals("INFO", logEventFactory.getRootLogger().getLevelThreshold().toString());
    }

    @Test
    public void shouldFindTestMethod() {
        LogEvent logEvent = new LogEvent("test", Level.INFO, "Testing");

        String formattedMessage = new DefaultTestLogEventConfigurator().createFormatter().apply(logEvent);
        assertTrue(formattedMessage + " should start with test name",
                formattedMessage.startsWith("TEST(DefaultLogEventConfiguratorTest.shouldFindTestMethod)"));

    }


    private void writeProps(Path file, Properties defaultProperties) throws IOException {
        try (FileWriter writer = new FileWriter(file.toFile())) {
            defaultProperties.store(writer, "Default configuration file");
        }
    }

    @After
    public void deleteConfigFiles() throws IOException {
        if (Files.isDirectory(propertiesDir)) {
            Files.list(propertiesDir).map(Path::toFile).forEach(file -> file.delete());
        }
    }


}
