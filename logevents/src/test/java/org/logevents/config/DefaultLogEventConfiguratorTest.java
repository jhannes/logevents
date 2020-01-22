package org.logevents.config;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.slf4j.Logger;
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
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultLogEventConfiguratorTest {

    private static final String CWD = Paths.get("").toAbsolutePath().getFileName().toString();

    private LogEventFactory factory = new LogEventFactory();
    private DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
    private Properties configuration = new Properties();
    private Path propertiesDir = Paths.get("target", "test-data", "properties");

    @Test
    public void shouldPrintWelcomeMessageIfNoConfiguration() {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.ERROR);
        configurator.applyConfigurationProperties(factory, new Properties());

        assertEquals(
                Arrays.asList("Logging by LogEvents (http://logevents.org). Create a file logevents.properties with the line logevents.status=INFO to suppress this message"),
                LogEventStatus.getInstance().getHeadMessageTexts(configurator, StatusEvent.StatusLevel.INFO));
    }

    @Test
    public void shouldNotPrintWelcomeMessageIfConfiguration() {
        configuration.setProperty("root", "WARN");
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals(Collections.emptyList(), LogEventStatus.getInstance().getHeadMessageTexts(configurator, StatusEvent.StatusLevel.INFO));
    }

    @Test
    public void shouldSetRootLevelFromProperties() {
        configuration.setProperty("logevents.status", "ERROR");
        configurator.applyConfigurationProperties(factory, configuration);
        String oldObserver = factory.getRootLogger().getObserver();
        configuration.setProperty("root", "TRACE");

        configurator.applyConfigurationProperties(factory, configuration);

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

        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals(Level.DEBUG, factory.getRootLogger().getLevelThreshold());
        assertEquals(
                "DateRollingLogEventObserver{"
                + "filename=FilenameFormatter{logs/application.log},"
                + "formatter=TTLLEventLogFormatter,fileRotationWorker=null}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldIncludeDefaultObservers() {
        configuration.setProperty("root", "DEBUG console,file");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(
                "CompositeLogEventObserver{["
                +"ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, "
                +"FileLogEventObserver{filename=FilenameFormatter{logs/" + CWD + "-test.log}," +
                        "formatter=TTLLEventLogFormatter," +
                        "fileRotationWorker=null}]}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldPartiallyConfigureDefaultObserver() {
        configuration.setProperty("observer.file.formatter", "ConsoleLogEventFormatter");
        configuration.setProperty("observer.console.threshold", "WARN");
        configuration.setProperty("root", "DEBUG console,file");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(
                "CompositeLogEventObserver{["
                +"ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, "
                +"FileLogEventObserver{filename=FilenameFormatter{logs/" + CWD + "-test.log}," +
                        "formatter=ConsoleLogEventFormatter,fileRotationWorker=null}]}",
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

        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals(Level.ERROR, factory.getLogger("org.example").getLevelThreshold());
        assertEquals(
                "CompositeLogEventObserver{"
                + "[CircularBufferLogEventObserver{size=0}, CircularBufferLogEventObserver{size=0}]}",
                factory.getLogger("org.example").getObserver());
    }

    @Test
    public void shouldKeepDefaultLoggersWithAdditionalRootLoggers() {
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("RootLoggerDelegator{ROOT,level=INFO,ownObserver=ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}}",
                factory.getLogger(Logger.ROOT_LOGGER_NAME).toString());

        configuration.setProperty("observer.buffer", "CircularBufferLogEventObserver");
        configuration.setProperty("root.observer.buffer", "DEBUG");
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("RootLoggerDelegator{ROOT,level=INFO,ownObserver=CompositeLogEventObserver{[" +
                        "ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, " +
                        "FixedLevelThresholdConditionalObserver{DEBUG -> CircularBufferLogEventObserver{size=0}}" +
                        "]}}",
                factory.getLogger(Logger.ROOT_LOGGER_NAME).toString());

        configuration.setProperty("root", "WARN");
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("RootLoggerDelegator{ROOT,level=WARN,ownObserver=CompositeLogEventObserver{[" +
                        "ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, " +
                        "FixedLevelThresholdConditionalObserver{DEBUG -> CircularBufferLogEventObserver{size=0}}" +
                        "]}}",
                factory.getLogger(Logger.ROOT_LOGGER_NAME).toString());
    }

    @Test
    public void shouldInstallMultipleRootLoggers() {
        configuration.setProperty("observer.buffer1", "CircularBufferLogEventObserver");
        configuration.setProperty("observer.buffer2", "CircularBufferLogEventObserver");
        configuration.setProperty("observer.buffer3", "CircularBufferLogEventObserver");
        configuration.setProperty("root", "ERROR buffer1");
        configuration.setProperty("root.observer.buffer2", "INFO");
        configuration.setProperty("root.observer.buffer3", "DEBUG");

        configurator.applyConfigurationProperties(factory, configuration);

        LoggerConfiguration logger = factory.getLogger("org.example");
        logger.debug("only to buffer3");
        logger.info("to buffer2 and buffer3");
        logger.error("to buffer1, buffer2 and buffer3");

        assertEquals(Arrays.asList("to buffer1, buffer2 and buffer3"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer1")).getMessages());
        assertEquals(Arrays.asList("to buffer2 and buffer3", "to buffer1, buffer2 and buffer3"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer2")).getMessages());
        assertEquals(Arrays.asList("only to buffer3", "to buffer2 and buffer3", "to buffer1, buffer2 and buffer3"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer3")).getMessages());
    }

    @Test
    public void shouldIncreaseLoggingWithMultipleRootObservers() {
        configuration.setProperty("observer.buffer", "CircularBufferLogEventObserver");
        configuration.setProperty("observer.ignored", "CircularBufferLogEventObserver");
        configuration.setProperty("root", "ERROR buffer");
        configuration.setProperty("root.observer.ignored", "INFO");
        configuration.setProperty("logger.org.example", "DEBUG");

        configurator.applyConfigurationProperties(factory, configuration);

        factory.getLogger("org.example").debug("DEBUG to enabled logger");
        factory.getLogger("com.example").debug("DEBUG to non-enabled logger");

        assertEquals(Arrays.asList("DEBUG to enabled logger"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer")).getMessages());
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

        configurator.applyConfigurationProperties(factory, configuration);

        factory.getLogger("org.example").error("Hello");
        assertEquals("Hello",
                ((CircularBufferLogEventObserver) factory.getObserver("buffer2")).singleMessage());
        assertEquals(Collections.emptyList(),
                new ArrayList<>(((CircularBufferLogEventObserver) factory.getObserver("buffer1")).getEvents()));

        assertEquals(
                "CircularBufferLogEventObserver{size=1}",
                factory.getLogger("org.example").getObserver());
    }

    @Test
    public void shouldRecoverToDefaultConfigurationOnInvalidConfigurationFile() throws IOException {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
        propertiesDir = Paths.get("target", "test-data", "invalid", "properties");
        deleteConfigFiles();
        Files.createDirectories(propertiesDir);

        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("logevents.what", "This should throw an error");
        writeProps(propertiesDir.resolve("logevents.properties"), defaultProperties);

        configurator = new DefaultLogEventConfigurator(propertiesDir);
        configurator.configure(factory);

        assertEquals(
                "RootLoggerDelegator{ROOT,level=INFO,ownObserver=ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}}",
                factory.getRootLogger().toString());
        assertEquals("Failed to load [logevents.properties]", LogEventStatus.getInstance().lastMessage().getMessage());
        assertEquals("Unknown configuration options: [what] for logevents. Expected options: [installExceptionHandler, jmx, status]",
                LogEventStatus.getInstance().lastMessage().getThrowable().getMessage());
    }

    @Test
    public void shouldWarnOnMisconfiguredObserver() {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);

        configuration.setProperty("logger.org.example", "ERROR faulty");
        configuration.setProperty("observer.faulty", "ConsoleLogEventObserver");
        configuration.setProperty("observer.faulty.nonExistingProperty", "Non existing property value");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(Collections.singletonList("Failed to create observer.faulty"),
                LogEventStatus.getInstance().getHeadMessages(configurator, StatusEvent.StatusLevel.ERROR)
                .stream().map(StatusEvent::getMessage).collect(Collectors.toList()));
    }

    @Test
    public void shouldUseOtherObserversOnMisconfiguredObserver() {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);

        configuration.setProperty("logger.org.example", "DEBUG console2,buffer");
        configuration.setProperty("includeParent.org.example", "false");
        configuration.setProperty("observer.buffer", "CircularBufferLogEventObserver");
        configuration.setProperty("observer.console2", "ConsoleLogEventObserver");
        configuration.setProperty("observer.console2.formatter", "PatternLogEventFormatter");

        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals(Collections.singletonList(new StatusEvent(
                configurator,
                "Failed to create observer.console2",
                StatusEvent.StatusLevel.ERROR,
                new LogEventConfigurationException("Missing required key <observer.console2.formatter.pattern> in <[includeParent.org.example, logger.org.example, observer.buffer, observer.console2, observer.console2.formatter]>"))
            ), LogEventStatus.getInstance().getHeadMessages(configurator, StatusEvent.StatusLevel.ERROR));

        factory.getLogger("org.example").debug("Hello");
        assertEquals("Hello",
                ((CircularBufferLogEventObserver) factory.getObserver("buffer")).singleMessage());
    }


    @Test
    public void shouldNotFailOnUnusedMisconfiguredObserver() {
        configuration.setProperty("logger.org.example", "ERROR console");
        configuration.setProperty("observer.faulty", "ConsoleLogEventFormatter");
        configuration.setProperty("observer.faulty.nonExistingProperty", "Non existing property value");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(Collections.emptyList(),
                LogEventStatus.getInstance().getHeadMessages(configurator, StatusEvent.StatusLevel.ERROR));
    }

    @Test
    public void shouldScanPropertiesFilesWhenFileIsChanged() throws IOException, InterruptedException {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
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
        configurator = new DefaultLogEventConfigurator(propertiesDir);
        configurator.configure(factory);

        assertEquals("ERROR", factory.getRootLogger().getLevelThreshold().toString());
        assertEquals("ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}", factory.getRootLogger().getObserver());

        firstProfileProperty.setProperty("root", "TRACE null");
        writeProps(propertiesDir.resolve("logevents-profile1.properties"), firstProfileProperty);
        Thread.sleep(70);

        assertEquals("TRACE", factory.getRootLogger().getLevelThreshold().toString());
        assertEquals("NullLogEventObserver", factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldWriteStatusLogIfConfigFileIsLocked() throws IOException {
        Assume.assumeTrue("File locking is not supported on Linux", isWindows());
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);

        propertiesDir = Paths.get("target", "test-data", "faulty" + System.currentTimeMillis());
        deleteConfigFiles();
        Files.createDirectories(propertiesDir);
        Path propsFile = propertiesDir.resolve("logevents-faultyconfig.properties");
        writeProps(propsFile, new Properties());

        try(RandomAccessFile file = new RandomAccessFile(propsFile.toFile(), "rw")) {
            try (FileLock ignored = file.getChannel().lock()) {
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

        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);

        String filename = "faulty" + System.currentTimeMillis() + ".properties";
        propertiesDir = Paths.get("target", "test-classes");
        Path propsFile = propertiesDir.resolve(filename);
        writeProps(propsFile, new Properties());

        try(RandomAccessFile file = new RandomAccessFile(propsFile.toFile(), "rw")) {
            try (FileLock ignored = file.getChannel().lock()) {
                DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator(propertiesDir);
                configurator.loadPropertiesFromFiles(Arrays.asList(filename));
            }
            assertEquals("Can't load " + filename,
                    LogEventStatus.getInstance().lastMessage().getMessage());
        }
    }

    @Test
    public void shouldScanPropertiesFilesWhenHigherPriorityFileIsAdded() throws IOException, InterruptedException {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
        propertiesDir = Paths.get("target", "test-data", "scan-new", "properties");
        deleteConfigFiles();
        Files.createDirectories(propertiesDir);

        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("root", "DEBUG");
        writeProps(propertiesDir.resolve("logevents.properties"), defaultProperties);

        System.setProperty("profiles", "production");
        configurator = new DefaultLogEventConfigurator(propertiesDir);
        configurator.configure(factory);

        assertEquals("DEBUG", factory.getRootLogger().getLevelThreshold().toString());
        Properties newPropertiesFile = new Properties();
        newPropertiesFile.setProperty("root", "INFO");
        writeProps(propertiesDir.resolve("logevents-production.properties"), newPropertiesFile);

        Thread.sleep(70);
        assertEquals("INFO", factory.getRootLogger().getLevelThreshold().toString());
    }

    @Test
    public void shouldFindTestMethod() {
        LogEvent logEvent = new LogEventSampler().build();

        String formattedMessage = new DefaultTestLogEventConfigurator()
                .createConsoleLogEventObserver(new Configuration())
                .getFormatter()
                .apply(logEvent);
        assertTrue(formattedMessage + " should start with test name",
                formattedMessage.contains("TEST(DefaultLogEventConfiguratorTest.shouldFindTestMethod(DefaultLogEventConfiguratorTest.java:"));
    }

    @Test
    public void shouldInstallDefaultExceptionHandler() throws InterruptedException {
        configuration.put("logevents.installExceptionHandler", "true");
        configurator.applyConfigurationProperties(factory, configuration);
        CircularBufferLogEventObserver rootObserver = new CircularBufferLogEventObserver();
        factory.setRootObserver(rootObserver);

        NumberFormatException exception = new NumberFormatException("Something happened");
        Thread thread = new Thread(() -> {
            throw exception;
        });
        thread.setName("SomeThread-121");
        thread.start();
        thread.join();

        assertEquals("Thread {} terminated with unhandled exception",
                rootObserver.singleMessage());
        assertEquals(exception, rootObserver.singleException());
    }


    public static void writeProps(Path file, Properties defaultProperties) throws IOException {
        try (FileWriter writer = new FileWriter(file.toFile())) {
            defaultProperties.store(writer, "Default configuration file");
        }
    }

    @After
    public void deleteConfigFiles() throws IOException {
        configurator.stopFileScanner();
        if (Files.isDirectory(propertiesDir)) {
            Files.list(propertiesDir).map(Path::toFile).forEach(File::delete);
        }
    }

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule();

    private boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }
}
