package org.logevents.config;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventFormatter;
import org.logevents.LogEventObserver;
import org.logevents.LogEventLogger;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.optional.junit.LogEventStatusRule;
import org.logevents.core.LoggerDelegator;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.core.LevelThresholdConditionalObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.MDC;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.logevents.optional.junit.LogEventSampler.OPS;

public class DefaultLogEventConfiguratorTest {

    private static final String CWD = Paths.get("").toAbsolutePath().getFileName().toString();

    private final LogEventFactory factory = new LogEventFactory();
    private DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
    private final Map<String, String> configuration = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Path propertiesDir = Paths.get("target", "test-data", "properties");

    @Test
    public void shouldPrintWelcomeMessageIfNoConfiguration() {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.ERROR);
        configurator.applyConfigurationProperties(factory, new HashMap<>());

        assertEquals(
                Arrays.asList("Logging by LogEvents (https://logevents.org). Create a file logevents.properties with the line logevents.status=INFO to suppress this message"),
                LogEventStatus.getInstance().getMessageTexts(configurator, StatusEvent.StatusLevel.INFO));
    }

    @Test
    public void shouldNotPrintWelcomeMessageIfConfiguration() {
        configuration.put("root", "WARN");
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals(Collections.emptyList(), LogEventStatus.getInstance().getMessageTexts(configurator, StatusEvent.StatusLevel.INFO));
    }

    @Test
    public void shouldSetRootLevelFromProperties() {
        configuration.put("logevents.status", "ERROR");
        configurator.applyConfigurationProperties(factory, configuration);
        String oldObserver = factory.getRootLogger().getObserver();
        configuration.put("root", "TRACE");

        configurator.applyConfigurationProperties(factory, configuration);

        assertTrue(factory.getLoggers() + " should be empty", factory.getLoggers().isEmpty());
        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG,TRACE}", factory.getRootLogger().getOwnFilter().toString());
        assertEquals(oldObserver, factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetRootLevelFromEnvironment() {
        ConsoleLogEventObserver observer = new ConsoleLogEventObserver();
        HashMap<String, Supplier<? extends LogEventObserver>> observers = new HashMap<>();
        observers.put("console", () -> observer);
        factory.setObservers(observers);
        Map<String, String> environment = new HashMap<>();
        environment.put("LOGEVENTS_ROOT", "DEBUG console");
        configurator.configureRootLogger(factory, configuration, environment);
        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG}", factory.getRootLogger().getOwnFilter().toString());
        assertTrue(factory.getRootLogger().isDebugEnabled());
        assertFalse(factory.getRootLogger().isTraceEnabled());
        assertEquals(observer.toString(), factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetRootObserverFromProperties() {
        Path logFile = Paths.get("logs", "application.log");
        configuration.put("root", "DEBUG file");
        configuration.put("observer.file", "DateRollingLogEventObserver");
        configuration.put("observer.file.filename", logFile.toString());

        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG}", factory.getRootLogger().getOwnFilter().toString());
        assertEquals(
                "DateRollingLogEventObserver{"
                + "filename=FilenameFormatter{logs/application.log},"
                + "formatter=TTLLLogEventFormatter,fileRotationWorker=null}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldIncludeDefaultObservers() {
        configuration.put("root", "DEBUG console,file");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(
                "CompositeLogEventObserver{["
                +"ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, "
                +"FileLogEventObserver{filename=FilenameFormatter{logs/" + CWD + "-test.log}," +
                        "formatter=TTLLLogEventFormatter," +
                        "fileRotationWorker=null}]}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldPartiallyConfigureDefaultObserver() {
        configuration.put("observer.file.formatter", "ConsoleLogEventFormatter");
        configuration.put("observer.console.threshold", "WARN");
        configuration.put("root", "DEBUG console,file");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(
                "CompositeLogEventObserver{["
                +"ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, "
                +"FileLogEventObserver{filename=FilenameFormatter{logs/" + CWD + "-test.log}," +
                        "formatter=ConsoleLogEventFormatter,fileRotationWorker=null}]}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldApplyFilterForThreshold() {
        configuration.put("observer.buffer", "CircularBufferLogEventObserver");
        configuration.put("observer.buffer.filter", "INFO,DEBUG@marker=OPS");
        configuration.put("root", "DEBUG buffer");

        configurator.applyConfigurationProperties(factory, configuration);
        factory.getRootLogger().debug("This should NOT be logged");
        factory.getRootLogger().debug(OPS, "This SHOULD be logged");

        CircularBufferLogEventObserver buffer = (CircularBufferLogEventObserver) factory.getObserver("buffer");
        assertEquals("This SHOULD be logged", buffer.singleMessage());
    }

    @Test
    public void shouldSupportNonLogging() {
        configuration.put("observer.buffer", "CircularBufferLogEventObserver");
        configuration.put("root", "NONE buffer");
        configuration.put("logger.org.example", "DEBUG");
        configuration.put("logger.org.example.hush", "NONE");

        configurator.applyConfigurationProperties(factory, configuration);

        factory.getLogger("org").error("This should NOT be logged");
        factory.getLogger("org.example").error("This SHOULD be logged");
        factory.getLogger("org.example.hush").error("This should also NOT be logged");

        java.util.logging.Logger.getLogger("org")
                .severe("This message to java.util.logging should NOT be logged");


        CircularBufferLogEventObserver buffer = (CircularBufferLogEventObserver) factory.getObserver("buffer");
        assertEquals("This SHOULD be logged", buffer.singleMessage());
    }

    @Test
    public void shouldSetLoggerObserverFromProperties() {
        configuration.put("root", "ERROR null");
        configuration.put("logger.org", "ERROR buffer1");
        configuration.put("logger.org.example", "ERROR buffer2");
        configuration.put("observer.buffer1", "CircularBufferLogEventObserver");
        configuration.put("observer.buffer2", "CircularBufferLogEventObserver");

        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("LogEventFilter{ERROR}", factory.getLogger("org.example").getOwnFilter().toString());
        assertEquals(
                "CompositeLogEventObserver{"
                + "[CircularBufferLogEventObserver{size=0,capacity=200}, CircularBufferLogEventObserver{size=0,capacity=200}]}",
                factory.getLogger("org.example").getObserver());
    }

    @Test
    public void shouldSetLoggerAndObserverFromEnvironment() throws IOException {
        Map<String, String> environment = new HashMap<>();
        environment.put("LOGEVENTS_OBSERVER_BUFFER", "CircularBufferLogEventObserver");
        environment.put("LOGEVENTS_LOGGER_ORG_EXAMPLE_DEMO", "DEBUG buffer");
        environment.put("LOGEVENTS_INCLUDEPARENT_ORG_EXAMPLE_DEMO", "false");

        Properties properties = new Properties();
        properties.put("observer.buffer.capacity", "15");
        deleteConfigFiles();
        writeProps(propertiesDir.resolve("logevents.properties"), properties);

        configurator = new DefaultLogEventConfigurator(propertiesDir, environment);
        configurator.configure(factory);

        CircularBufferLogEventObserver buffer = (CircularBufferLogEventObserver)factory.getObserver("buffer");
        assertEquals("CircularBufferLogEventObserver{size=0,capacity=15}", buffer.toString());

        LogEventLogger logger = factory.getLogger("org.example.demo");
        assertTrue(logger.isDebugEnabled());
        assertFalse(logger.isTraceEnabled());
        assertEquals("CircularBufferLogEventObserver{size=0,capacity=15}", logger.getObserver());

        logger.info("Hello");
        assertEquals("Hello", buffer.singleMessage());
        assertEquals("org.example.demo", buffer.getEvents().get(0).getLoggerName());
    }

    @Test
    public void shouldConfigureObserverFromEnvironment() {
        HashMap<String, String> environment = new HashMap<>();
        environment.put("LOGEVENTS_OBSERVER_BUFFER_CAPACITY", "666");
        Configuration configuration = new Configuration(new HashMap<>(), "observer.buffer", environment);
        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver(configuration);
        assertEquals("CircularBufferLogEventObserver{size=0,capacity=666}", observer.toString());
    }

    @Test
    public void shouldKeepDefaultLoggersWithAdditionalRootLoggers() {
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("RootLoggerDelegator{ROOT,filter=LogEventFilter{ERROR,WARN,INFO},ownObserver=ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}}",
                factory.getLogger(Logger.ROOT_LOGGER_NAME).toString());

        configuration.put("observer.buffer", "CircularBufferLogEventObserver");
        configuration.put("root.observer.buffer", "DEBUG");
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("RootLoggerDelegator{ROOT,filter=LogEventFilter{ERROR,WARN,INFO},ownObserver=CompositeLogEventObserver{[" +
                        "ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, " +
                        "LevelThresholdConditionalObserver{DEBUG -> CircularBufferLogEventObserver{size=0,capacity=200}}" +
                        "]}}",
                factory.getLogger(Logger.ROOT_LOGGER_NAME).toString());

        configuration.put("root", "WARN");
        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals("RootLoggerDelegator{ROOT,filter=LogEventFilter{ERROR,WARN},ownObserver=CompositeLogEventObserver{[" +
                        "ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}, " +
                        "LevelThresholdConditionalObserver{DEBUG -> CircularBufferLogEventObserver{size=0,capacity=200}}" +
                        "]}}",
                factory.getLogger(Logger.ROOT_LOGGER_NAME).toString());
    }

    @Test
    public void shouldInstallMultipleRootLoggers() {
        configuration.put("observer.buffer1", "CircularBufferLogEventObserver");
        configuration.put("observer.buffer2", "CircularBufferLogEventObserver");
        configuration.put("observer.buffer3", "CircularBufferLogEventObserver");
        configuration.put("root", "DEBUG buffer1");
        configuration.put("root.observer.buffer2", "INFO");
        configuration.put("root.observer.buffer3", "WARN");

        configurator.applyConfigurationProperties(factory, configuration);

        LogEventLogger logger = factory.getLogger("org.example");
        logger.debug("only to buffer1");
        logger.info("to buffer1 and buffer2");
        logger.error("to buffer1, buffer2 and buffer3");

        assertEquals(Arrays.asList("only to buffer1", "to buffer1 and buffer2", "to buffer1, buffer2 and buffer3"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer1")).getMessages());
        assertEquals(Arrays.asList("to buffer1 and buffer2", "to buffer1, buffer2 and buffer3"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer2")).getMessages());
        assertEquals(Arrays.asList("to buffer1, buffer2 and buffer3"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer3")).getMessages());
    }

    @Test
    public void shouldInstallRootObserverFromEnvironment() {
        configuration.put("observer.buffer1", "CircularBufferLogEventObserver");
        configurator.applyConfigurationProperties(factory, configuration);

        HashMap<String, LogEventObserver> globalObservers = new HashMap<>();
        Map<String, String> environment = new HashMap<>();
        environment.put("LOGEVENTS_ROOT_OBSERVER_BUFFER1", "ERROR");
        configurator.createGlobalObserversFromEnvironment(globalObservers, factory, environment);
        assertEquals(Collections.singleton("buffer1"), globalObservers.keySet());
        assertEquals(Level.ERROR,
                ((LevelThresholdConditionalObserver) globalObservers.get("buffer1")).getThreshold());
    }

    @Test
    public void shouldIncreaseLoggingWithMultipleRootObservers() {
        configuration.put("observer.buffer", "CircularBufferLogEventObserver");
        configuration.put("observer.ignored", "CircularBufferLogEventObserver");
        configuration.put("root", "ERROR buffer");
        configuration.put("root.observer.ignored", "INFO");
        configuration.put("logger.org.example", "DEBUG");

        configurator.applyConfigurationProperties(factory, configuration);

        factory.getLogger("org.example").debug("DEBUG to enabled logger");
        factory.getLogger("com.example").debug("DEBUG to non-enabled logger");

        assertEquals(Arrays.asList("DEBUG to enabled logger"),
                ((CircularBufferLogEventObserver) factory.getObserver("buffer")).getMessages());
    }

    @Test
    public void shouldAvoidDuplicateObserversForLogger() {
        configuration.put("observer.testObserver", "CircularBufferLogEventObserver");
        configuration.put("observer.ignored", "CircularBufferLogEventObserver");
        configuration.put("root", "INFO testObserver");
        configuration.put("logger.org", "DEBUG testObserver,ignored");
        configuration.put("logger.org.example", "DEBUG testObserver");

        configurator.applyConfigurationProperties(factory, configuration);

        factory.getLogger("org.example").info("Hello");
        assertEquals(Arrays.asList("Hello"), ((CircularBufferLogEventObserver)factory.getObserver("testObserver")).getMessages());
    }

    @Test
    public void shouldConfigureMdcThreshold() {
        configuration.put("observer.buffer", "CircularBufferLogEventObserver");
        configuration.put("logger.org.example",
                "INFO,DEBUG@mdc:user=johannes|skywalker,DEBUG@mdc:operation=important buffer");
        configuration.put("includeParent.org.example", "false");

        configurator.applyConfigurationProperties(factory, configuration);

        CircularBufferLogEventObserver buffer = (CircularBufferLogEventObserver) factory.getObserver("buffer");
        Logger logger = factory.getLogger("org.example.sublevel");
        MDC.put("user", "johannes");
        logger.debug("should be included because of MDC");
        logger.debug("should be included because of MDC: arg={}", "argvalue");
        logger.debug("should be included because of MDC (with exception)", new Exception());
        logger.trace("should be excluded despite of MDC: arg={}", "argvalue");
        MDC.put("operation", "important");
        logger.debug("should be included because of two MDCs: arg1={}, arg2={}", "one", "two");
        MDC.put("user", "boring");
        logger.debug("should be included because of second MDC: arg1={}, arg2={}, arg3={}", "one", "two", "three");
        MDC.put("operation", "boring");
        logger.debug("should be excluded because no MDC");
        logger.info("should be included because over threshold");

        List<String> expected = Arrays.asList(
                "should be included because of MDC",
                "should be included because of MDC: arg={}",
                "should be included because of MDC (with exception)",
                "should be included because of two MDCs: arg1={}, arg2={}",
                "should be included because of second MDC: arg1={}, arg2={}, arg3={}",
                "should be included because over threshold"
        );
        assertEquals(expected, buffer.getMessages());
    }

    @Test
    public void shouldConfigureMdcThresholdWithDefaultObserver() {
        configuration.put("observer.buffer", "CircularBufferLogEventObserver");
        configuration.put("root", "WARN buffer");
        configuration.put("logger.org.example", "INFO,DEBUG@mdc:user=johannes");
        configurator.applyConfigurationProperties(factory, configuration);

        CircularBufferLogEventObserver buffer = (CircularBufferLogEventObserver) factory.getObserver("buffer");
        LoggerDelegator logger = (LoggerDelegator) factory.getLogger("org.example.sublevel");
        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG=RequiredMdcCondition{user in [johannes]}}", logger.getEffectiveFilter().toString());

        logger.debug("Excluded");
        MDC.put("user", "johannes");
        logger.debug("Included");
        assertEquals(Arrays.asList("Included"), buffer.getMessages());
    }

    @Test
    public void shouldSetNonInheritingLoggerObserverFromProperties() {
        configuration.put("root", "ERROR null");
        configuration.put("logger.org", "ERROR buffer1");
        configuration.put("logger.org.example", "ERROR buffer2");
        configuration.put("includeParent.org.example", "false");
        configuration.put("observer.buffer1", "CircularBufferLogEventObserver");
        configuration.put("observer.buffer2", "CircularBufferLogEventObserver");

        configurator.applyConfigurationProperties(factory, configuration);

        factory.getLogger("org.example").error("Hello");
        assertEquals("Hello",
                ((CircularBufferLogEventObserver) factory.getObserver("buffer2")).singleMessage());
        assertEquals(Collections.emptyList(),
                new ArrayList<>(((CircularBufferLogEventObserver) factory.getObserver("buffer1")).getEvents()));

        assertEquals(
                "CircularBufferLogEventObserver{size=1,capacity=200}",
                factory.getLogger("org.example").getObserver());
    }

    @Test
    public void shouldRecoverToDefaultConfigurationOnInvalidConfigurationFile() throws IOException {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
        propertiesDir = Paths.get("target", "test-data", "invalid", "properties");
        deleteConfigFiles();

        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("logevents.what", "This should throw an error");
        writeProps(propertiesDir.resolve("logevents.properties"), defaultProperties);

        configurator = new DefaultLogEventConfigurator(propertiesDir);
        configurator.configure(factory);

        assertEquals(
                "RootLoggerDelegator{ROOT,filter=LogEventFilter{ERROR,WARN,INFO},ownObserver=ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter},inheritsParent=true}",
                factory.getRootLogger().toString());
        StatusEvent lastMessage = LogEventStatus.getInstance().getMessages(configurator, StatusEvent.StatusLevel.FATAL).get(0);
        assertEquals("Failed to load [logevents.properties, logevents-test.properties]", lastMessage.getMessage());
        assertEquals("Unknown configuration options: [what] for logevents. Expected options: [installExceptionHandler, jmx, status]",
                lastMessage.getThrowable().getMessage());
    }

    @Test
    public void shouldWarnOnMisconfiguredObserver() {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);

        configuration.put("logger.org.example", "ERROR faulty");
        configuration.put("observer.faulty", "ConsoleLogEventObserver");
        configuration.put("observer.faulty.nonExistingProperty", "Non existing property value");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(Collections.singletonList("Failed to create observer.faulty"),
                LogEventStatus.getInstance().getMessages(configurator, StatusEvent.StatusLevel.ERROR)
                .stream().map(StatusEvent::getMessage).collect(Collectors.toList()));
    }

    @Test
    public void shouldUseOtherObserversOnMisconfiguredObserver() {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);

        configuration.put("logger.org.example", "DEBUG console2,buffer");
        configuration.put("includeParent.org.example", "false");
        configuration.put("observer.buffer", "CircularBufferLogEventObserver");
        configuration.put("observer.console2", "ConsoleLogEventObserver");
        configuration.put("observer.console2.formatter", "PatternLogEventFormatter");

        configurator.applyConfigurationProperties(factory, configuration);
        assertEquals(Collections.singletonList(new StatusEvent(
                configurator,
                "Failed to create observer.console2",
                StatusEvent.StatusLevel.ERROR,
                new LogEventConfigurationException("Missing required key <observer.console2.formatter.pattern> in <[includeParent.org.example, logger.org.example, observer.buffer, observer.console2, observer.console2.formatter]>"))
            ), LogEventStatus.getInstance().getMessages(configurator, StatusEvent.StatusLevel.ERROR));

        factory.getLogger("org.example").debug("Hello");
        assertEquals("Hello",
                ((CircularBufferLogEventObserver) factory.getObserver("buffer")).singleMessage());
    }


    @Test
    public void shouldNotFailOnUnusedMisconfiguredObserver() {
        configuration.put("logger.org.example", "ERROR console");
        configuration.put("observer.faulty", "ConsoleLogEventFormatter");
        configuration.put("observer.faulty.nonExistingProperty", "Non existing property value");

        configurator.applyConfigurationProperties(factory, configuration);

        assertEquals(Collections.emptyList(),
                LogEventStatus.getInstance().getMessages(configurator, StatusEvent.StatusLevel.ERROR));
    }

    @Test
    public void shouldScanPropertiesFilesWhenFileIsChanged() throws IOException, InterruptedException {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
        propertiesDir = Paths.get("target", "test-data", "scan-change", "properties");
        deleteConfigFiles();

        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("root", "DEBUG");
        defaultProperties.setProperty("observer.console", "ConsoleLogEventObserver");
        writeProps(propertiesDir.resolve("logevents.properties"), defaultProperties);

        Properties firstProfileProperty = new Properties();
        firstProfileProperty.setProperty("root", "ERROR console");
        writeProps(propertiesDir.resolve("logevents-profile1.properties"), firstProfileProperty);

        System.setProperty("profiles", "profile1");
        configurator = new DefaultLogEventConfigurator(propertiesDir);
        configurator.configure(factory);

        assertEquals("LogEventFilter{ERROR}", factory.getRootLogger().getOwnFilter().toString());
        assertTrue(factory.getRootLogger().isErrorEnabled());
        assertFalse(factory.getRootLogger().isWarnEnabled());
        assertEquals("ConsoleLogEventObserver{formatter=ConsoleLogEventFormatter}", factory.getRootLogger().getObserver());

        firstProfileProperty.setProperty("root", "TRACE null");
        writeProps(propertiesDir.resolve("logevents-profile1.properties"), firstProfileProperty);
        Thread.sleep(120);

        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG,TRACE}", factory.getRootLogger().getOwnFilter().toString());
        assertEquals("NullLogEventObserver", factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldWriteStatusLogIfConfigFileIsLocked() throws IOException {
        Assume.assumeTrue("File locking is not supported on Linux", isWindows());
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);

        propertiesDir = Paths.get("target", "test-data", "faulty" + System.currentTimeMillis());
        deleteConfigFiles();
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

        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("root", "DEBUG");
        writeProps(propertiesDir.resolve("logevents.properties"), defaultProperties);

        System.setProperty("profiles", "production");
        configurator = new DefaultLogEventConfigurator(propertiesDir);
        configurator.configure(factory);

        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG}", factory.getRootLogger().getOwnFilter().toString());
        Properties newPropertiesFile = new Properties();
        newPropertiesFile.setProperty("root", "INFO");
        writeProps(propertiesDir.resolve("logevents-production.properties"), newPropertiesFile);

        Thread.sleep(70);
        assertEquals("LogEventFilter{ERROR,WARN,INFO}", factory.getRootLogger().getOwnFilter().toString());
        assertTrue(factory.getRootLogger().isInfoEnabled());
        assertFalse(factory.getRootLogger().isDebugEnabled());
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
    public void shouldFindTestMethodFromOtherThread() throws InterruptedException {
        AtomicReference<String> formattedMessage = new AtomicReference<>();
        LogEventFormatter formatter = new DefaultTestLogEventConfigurator()
                .createConsoleLogEventObserver(new Configuration())
                .getFormatter();
        Thread thread = new Thread(() -> formattedMessage.set(formatter.apply(new LogEventSampler().build())));
        thread.start();
        thread.join();

        assertTrue(formattedMessage.get() + " should start with test name",
                formattedMessage.get().contains("TEST(DefaultLogEventConfiguratorTest.shouldFindTestMethodFromOtherThread(DefaultLogEventConfiguratorTest.java:"));
    }

    @Test
    public void shouldLogNullWhenTestThreadIsNotFound() throws InterruptedException {
        AtomicReference<String> formattedMessage = new AtomicReference<>();
        HashMap<String, String> properties = new HashMap<>();
        properties.put("observer.testThreadName", "non-existing");
        LogEventFormatter formatter = new DefaultTestLogEventConfigurator()
                .createConsoleLogEventObserver(new Configuration(properties, "observer"))
                .getFormatter();
        Thread thread = new Thread(() -> formattedMessage.set(formatter.apply(new LogEventSampler().build())));
        thread.start();
        thread.join();

        assertTrue(formattedMessage.get() + " should not find test name",
                formattedMessage.get().contains("TEST(null)"));
    }

    @Test
    public void shouldInstallDefaultExceptionHandler() throws InterruptedException {
        Thread.setDefaultUncaughtExceptionHandler(null);
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
        Files.createDirectories(file.getParent());
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
