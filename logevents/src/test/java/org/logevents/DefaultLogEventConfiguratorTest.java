package org.logevents;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultLogEventConfiguratorTest {

    private static final String FS = System.getProperty("file.separator");
    private static final String CWD = Paths.get("").toAbsolutePath().getFileName().toString();

    private LogEventFactory factory = new LogEventFactory();
    private DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
    private Properties configuration = new Properties();
    private Path propertiesDir = Paths.get("target", "test-data", "properties");
    private static StatusEvent.StatusLevel oldThreshold;

    @Test
    public void shouldSetRootLevelFromProperties() {
        configurator.configureLogEventFactory(factory, configuration);
        String oldObserver = factory.getRootLogger().getObserver();
        configuration.setProperty("root", "TRACE");

        configurator.configureLogEventFactory(factory, configuration);

        assertTrue(factory.getLoggers() + " should be empty", factory.getLoggers().isEmpty());
        assertEquals(Level.TRACE, factory.getRootLogger().getLevelThreshold());
        assertEquals(oldObserver, factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetRootObserverFromProperties() {
        Path logFile = Paths.get("logs", "application.log");
        configuration.setProperty("root", "DEBUG file");
        configuration.setProperty("observer.file", "DateRollingLogEventObserver");
        configuration.setProperty("observer.file.filename", logFile.toString());

        configurator.configureLogEventFactory(factory, configuration);
        assertEquals(Level.DEBUG, factory.getRootLogger().getLevelThreshold());
        assertEquals(
                "DateRollingLogEventObserver{"
                + "filename=" + logFile + ","
                + "formatter=TTLLEventLogFormatter}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldIncludeDefaultObservers() {
        configuration.setProperty("root", "DEBUG console,file");

        configurator.configureLogEventFactory(factory, configuration);

        assertEquals(
                "CompositeLogEventObserver{["
                +"ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, "
                +"FileLogEventObserver{filename=logs" + FS + CWD + "-test.log,formatter=TTLLEventLogFormatter}]}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldPartiallyConfigureDefaultObserver() {
        configuration.setProperty("observer.file.formatter", "ConsoleLogEventFormatter");
        configuration.setProperty("observer.console.threshold", "WARN");
        configuration.setProperty("root", "DEBUG console,file");

        configurator.configureLogEventFactory(factory, configuration);

        assertEquals(
                "CompositeLogEventObserver{["
                +"ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, "
                +"FileLogEventObserver{filename=logs" + FS + CWD + "-test.log,formatter=ConsoleLogEventFormatter}]}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetLoggerObserverFromProperties() {
        configuration.setProperty("root", "ERROR null");
        configuration.setProperty("logger.org", "ERROR buffer1");
        configuration.setProperty("logger.org.example", "ERROR buffer2");
        configuration.setProperty("observer.null", "NullLogEventObserver");
        configuration.setProperty("observer.buffer1", "CircularBufferLogEventObserver");
        configuration.setProperty("observer.buffer2", "CircularBufferLogEventObserver");

        configurator.configureLogEventFactory(factory, configuration);
        assertEquals(Level.ERROR, factory.getLogger("org.example").getLevelThreshold());
        assertEquals(
                "CompositeLogEventObserver{"
                + "[CircularBufferLogEventObserver{size=0}, CircularBufferLogEventObserver{size=0}]}",
                factory.getLogger("org.example").getObserver());
    }

    @Test
    public void shouldSetNonInheritingLoggerObserverFromProperties() {
        configuration.setProperty("root", "ERROR null");
        configuration.setProperty("logger.org", "ERROR buffer1");
        configuration.setProperty("logger.org.example", "ERROR buffer2");
        configuration.setProperty("includeParent.org.example", "false");
        configuration.setProperty("observer.null", "NullLogEventObserver");
        configuration.setProperty("observer.buffer1", "CircularBufferLogEventObserver");
        configuration.setProperty("observer.buffer2", "CircularBufferLogEventObserver");

        configurator.configureLogEventFactory(factory, configuration);

        factory.getLogger("org.example").error("Hello");
        assertEquals("Hello",
                ((CircularBufferLogEventObserver) configurator.getObserver("buffer2")).singleMessage());
        assertEquals(Collections.emptyList(),
                new ArrayList<>(((CircularBufferLogEventObserver) configurator.getObserver("buffer1")).getEvents()));

        assertEquals(
                "CircularBufferLogEventObserver{size=1}",
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
        Thread.sleep(20);

        assertEquals("TRACE", logEventFactory.getRootLogger().getLevelThreshold().toString());
        assertEquals("NullLogEventObserver", logEventFactory.getRootLogger().getObserver());
    }

    @Test
    public void shouldWriteStatusLogIfConfigFileIsLocked() throws IOException {
        Assume.assumeTrue("File locking is not supported on Linux", isWindows());

        propertiesDir = Paths.get("target", "test-data", "faulty" + System.currentTimeMillis());
        deleteConfigFiles();
        Files.createDirectories(propertiesDir);
        Path propsFile = propertiesDir.resolve("logevents-faultyconfig.properties");
        writeProps(propsFile, new Properties());

        try(RandomAccessFile file = new RandomAccessFile(propsFile.toFile(), "rw")) {
            try (FileLock lock = file.getChannel().lock()) {
                DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator(propertiesDir);
                configurator.loadPropertiesFromFiles(Arrays.asList("logevents-faultyconfig.properties"));
            }
            assertEquals("Can't load logevents-faultyconfig.properties",
                    LogEventStatus.getInstance().lastMessage().getMessage());
        }
    }


    @Test
    public void shouldWriteStatusLogIfConfigResourceIsLocked() throws IOException {
        Assume.assumeTrue("File locking is not supported on Linux", isWindows());

        String filename = "faulty" + System.currentTimeMillis() + ".properties";
        propertiesDir = Paths.get("target", "test-classes");
        Path propsFile = propertiesDir.resolve(filename);
        writeProps(propsFile, new Properties());

        try(RandomAccessFile file = new RandomAccessFile(propsFile.toFile(), "rw")) {
            try (FileLock lock = file.getChannel().lock()) {
                DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator(propertiesDir);
                configurator.loadPropertiesFromFiles(Arrays.asList(filename));
            }
            assertEquals("Can't load " + filename,
                    LogEventStatus.getInstance().lastMessage().getMessage());
        }
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
        newPropertiesFile.setProperty("root", "INFO");
        writeProps(propertiesDir.resolve("logevents-production.properties"), newPropertiesFile);

        Thread.sleep(20);
        assertEquals("INFO", logEventFactory.getRootLogger().getLevelThreshold().toString());
    }

    @Test
    public void shouldFindTestMethod() {
        LogEvent logEvent = new LogEvent("test", Level.INFO, "Testing", new Object[0]);

        String formattedMessage = new DefaultTestLogEventConfigurator()
                .createConsoleLogEventObserver(new Properties())
                .getFormatter()
                .apply(logEvent);
        assertTrue(formattedMessage + " should start with test name",
                formattedMessage.contains("TEST(DefaultLogEventConfiguratorTest.shouldFindTestMethod)"));
    }


    private void writeProps(Path file, Properties defaultProperties) throws IOException {
        try (FileWriter writer = new FileWriter(file.toFile())) {
            defaultProperties.store(writer, "Default configuration file");
        }
    }

    @BeforeClass
    public static void turnOffStatusLogging() {
        oldThreshold = LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
    }

    @AfterClass
    public static void restoreStatusLogging() {
        LogEventStatus.getInstance().setThreshold(oldThreshold);
    }

    @After
    public void deleteConfigFiles() throws IOException {
        if (Files.isDirectory(propertiesDir)) {
            Files.list(propertiesDir).map(Path::toFile).forEach(File::delete);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }
}
