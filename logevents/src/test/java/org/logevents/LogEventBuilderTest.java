package org.logevents;

import org.junit.Before;
import org.junit.Test;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.optional.junit.LogEventSampler;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class LogEventBuilderTest {

    private final CircularBufferLogEventObserver buffer = new CircularBufferLogEventObserver();
    private final LogEventFactory factory = new LogEventFactory();
    private LogEventLogger logger;

    @Before
    public void setUp() throws Exception {
        factory.setRootLevel(Level.TRACE);
        factory.setRootObserver(buffer);
        logger = factory.getLogger("foo.bar");
    }

    @Test
    public void shouldGenerateLogEventAtLevel() {
        logger.atDebug().setMessage("test").addMarker(LogEventSampler.OPS).log();
        LogEvent logEvent = buffer.singleLogEvent();
        assertEquals("foo.bar", logEvent.getLoggerName());
        assertEquals(Level.DEBUG, logEvent.getLevel());
        assertEquals("test", logEvent.getMessage());
        assertEquals(LogEventSampler.OPS, logEvent.getMarker());
    }

    @Test
    public void shouldGenerateMessageWithArguments() {
        logger.atError().setMessage(() -> "test {} {}").addArgument(123).addArgument(ArrayList::new).log();
        assertEquals("test 123 []", buffer.singleLogEvent().getMessage(new MessageFormatter()));
    }

    @Test
    public void shouldLogMessageWithArguments() {
        logger.atError().log("test {} {}", 123, Arrays.asList("ABC", "PQR"));
        assertEquals("test 123 [ABC, PQR]", buffer.singleLogEvent().getMessage(new MessageFormatter()));
        buffer.clear();
        logger.atWarn().addArgument(Arrays.asList(1, 2)).log("test {} {}", 123);
        assertEquals("test [1, 2] 123", buffer.singleLogEvent().getMessage(new MessageFormatter()));
        buffer.clear();
        logger.atInfo().log("test {} {} {}", "XYZ", "ABC", "PQR");
        assertEquals("test XYZ ABC PQR", buffer.singleLogEvent().getMessage(new MessageFormatter()));
    }

    @Test
    public void shouldLogWithSupplier() {
        logger.atDebug().log(() -> "something");
        assertEquals("something", buffer.singleLogEvent().getMessage());
    }

    @Test
    public void shouldGenerateLogEventAtAllLevels() {
        for (Level level : Level.values()) {
            logger.atLevel(level).log(level::toString);
            assertEquals(level, buffer.singleLogEvent().getLevel());
            assertEquals(level.toString(), buffer.singleLogEvent().getMessage());
            buffer.clear();
        }
    }
}