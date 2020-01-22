package org.logevents.jmx;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LoggerConfiguration;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.FixedLevelThresholdConditionalObserver;
import org.logevents.observers.LevelThresholdConditionalObserver;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoggerMXBeanAdaptorTest {


    private static class TestObserver implements LogEventObserver {
        private final String name;

        private TestObserver(String name) {
            this.name = name;
        }

        @Override
        public void logEvent(LogEvent logEvent) {

        }

        @Override
        public String toString() {
            return "TestObserver{" + name + '}';
        }
    }

    private LogEventFactory factory = new LogEventFactory();
    private LoggerConfiguration logger = factory.getLogger("org.example");
    private LoggerMXBeanAdaptor mbean = new LoggerMXBeanAdaptor(factory, logger);

    @Test
    public void shouldManipulateLogger() {
        factory.setLevel(factory.getLogger("org"), Level.INFO);
        assertNull(mbean.getLevel());

        mbean.setLevel(Level.WARN);
        assertEquals(Level.WARN, mbean.getLevel());
        assertEquals(Level.WARN, logger.getLevelThreshold());
        assertEquals(Level.INFO, factory.getLogger("org").getLevelThreshold());
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
