package org.logevents;

import org.junit.Test;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.slf4j.event.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class LogEventFactoryTest {

    private final LogEventFactory factory = new LogEventFactory();

    @Test
    public void aliasLoggersShouldHaveSameObserverInstance() {
        CircularBufferLogEventObserver buffer1 = new CircularBufferLogEventObserver();
        CircularBufferLogEventObserver buffer2 = new CircularBufferLogEventObserver();

        LoggerConfiguration childLogger = factory.getLogger("org.example.app");
        factory.setObserver(childLogger, buffer1, false);
        LoggerConfiguration aliasChildLogger = factory.getLogger("org.example.App");

        aliasChildLogger.warn("Hello");
        assertEquals("Hello", buffer1.singleMessage());

        factory.setObserver(aliasChildLogger, buffer2);
        childLogger.warn("There");
        assertEquals("There", buffer2.singleMessage());
    }

    @Test
    public void shouldAddObserversToAliasLoggers() {
        LoggerConfiguration logger = factory.getLogger("org.example.app");
        LoggerConfiguration aliasLogger = factory.getLogger("org.example.App");
        factory.setObserver(logger, new CircularBufferLogEventObserver(), false);
        factory.addObserver(logger, new CircularBufferLogEventObserver());
        assertEquals(aliasLogger.getObserver(), logger.getObserver());

        assertEquals(factory.getLogger("ORG.EXAMPLE.APP").getObserver(), logger.getObserver());
    }

    @Test
    public void shouldUpdateLevelForAllChildLoggers() {
        LoggerConfiguration parentLogger = factory.getLogger("org.example");
        LoggerConfiguration childLogger = factory.getLogger("org.example.app");

        LoggerConfiguration aliasChildLogger = factory.getLogger("org.example.App");
        assertNotEquals(childLogger.getName(), aliasChildLogger.getName());
        factory.setLevel(parentLogger, Level.ERROR);

        assertEquals(Level.ERROR, parentLogger.getEffectiveFilter().getThreshold());
        assertEquals(Level.ERROR, childLogger.getEffectiveFilter().getThreshold());
        assertEquals(Level.ERROR, aliasChildLogger.getEffectiveFilter().getThreshold());
    }

    @Test
    public void shouldPropagateLevelToAliasLoggers() {
        LoggerConfiguration logger = factory.getLogger("org.example.app");
        factory.setLevel(logger, Level.ERROR);

        LoggerConfiguration aliasLogger = factory.getLogger("org.example.App");
        assertEquals(logger.getEffectiveFilter().getThreshold(), aliasLogger.getEffectiveFilter().getThreshold());
        factory.setLevel(logger, Level.WARN);
        assertEquals(logger.getEffectiveFilter().getThreshold(), aliasLogger.getEffectiveFilter().getThreshold());
    }
}
