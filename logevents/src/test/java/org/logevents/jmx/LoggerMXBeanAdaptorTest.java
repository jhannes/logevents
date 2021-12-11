package org.logevents.jmx;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LogEventLogger;
import org.logevents.core.CompositeLogEventObserver;
import org.logevents.core.FixedLevelThresholdConditionalObserver;
import org.logevents.core.LevelThresholdConditionalObserver;
import org.logevents.observers.TestObserver;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoggerMXBeanAdaptorTest {


    private LogEventFactory factory = new LogEventFactory();
    private LogEventLogger logger = factory.getLogger("org.example");
    private LoggerMXBeanAdaptor mbean = new LoggerMXBeanAdaptor(factory, logger);

    @Test
    public void shouldManipulateLogger() {
        factory.setLevel(factory.getLogger("org"), Level.INFO);
        assertNull(mbean.getFilter());

        mbean.setLevel(Level.WARN);
        assertEquals("LogEventFilter{ERROR,WARN}", mbean.getFilter().toString());
        assertEquals("LogEventFilter{ERROR,WARN}", logger.getOwnFilter().toString());
        assertEquals("LogEventFilter{ERROR,WARN,INFO}", factory.getLogger("org").getOwnFilter().toString());
    }

    @Test
    public void shouldShowObservers() {
        LogEventObserver observers = CompositeLogEventObserver.combine(
                new FixedLevelThresholdConditionalObserver(Level.DEBUG, new TestObserver("global")),
                new LevelThresholdConditionalObserver(Level.INFO, new TestObserver("info")),
                new LevelThresholdConditionalObserver(Level.WARN, new TestObserver("warn")),
                new LevelThresholdConditionalObserver(Level.ERROR, new TestObserver("error"))
        );
        factory.setObserver(logger, observers);
        factory.setLevel(logger, Level.WARN);
        assertEquals("CompositeLogEventObserver{[" +
                "FixedLevelThresholdConditionalObserver{DEBUG -> TestObserver{global}}, " +
                "LevelThresholdConditionalObserver{INFO -> TestObserver{info}}, " +
                "LevelThresholdConditionalObserver{WARN -> TestObserver{warn}}, " +
                "LevelThresholdConditionalObserver{ERROR -> TestObserver{error}}]}", mbean.getObserver());

        assertEquals(new ArrayList<>(), mbean.getTraceObservers());
        assertEquals(Arrays.asList("TestObserver{global}"), mbean.getDebugObservers());
        assertEquals(Arrays.asList("TestObserver{global}"), mbean.getInfoObservers());
        assertEquals(Arrays.asList("TestObserver{global}", "TestObserver{info}", "TestObserver{warn}"),
                mbean.getWarnObservers());
        assertEquals(Arrays.asList("TestObserver{global}", "TestObserver{info}", "TestObserver{warn}", "TestObserver{error}"),
                mbean.getErrorObservers());
    }

}
