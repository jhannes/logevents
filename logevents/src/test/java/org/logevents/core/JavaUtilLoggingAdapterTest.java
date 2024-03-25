package org.logevents.core;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.optional.junit.LogEventRule;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class JavaUtilLoggingAdapterTest {

    @Rule
    public LogEventRule rule = new LogEventRule(Level.TRACE, LogEventFactory.getInstance().getRootLogger());

    private final Logger logger = Logger.getLogger("org.example.TestLogger");

    @Test
    public void shouldLogFinestLevelToTrace() {
        LogEventFactory.getInstance().setLevel(logger.getName(), Level.TRACE);
        logger.finest("Very very fine message");
        rule.assertContainsMessage(Level.TRACE, "Very very fine message");
    }

    @Test
    public void shouldLogSevereLevelToTrace() {
        logger.severe("A very serious message");
        rule.assertContainsMessage(Level.ERROR, "A very serious message");
    }

    @Test
    public void shouldLogCustomHighLevelToError() {
        IOException e = new IOException("My IOException");
        java.util.logging.Level superSevere = new java.util.logging.Level("SUPER_SEVER", java.util.logging.Level.SEVERE.intValue()+100) {

        };
        logger.log(superSevere, "A super severe message", e);
        rule.assertContainsMessage(Level.ERROR, "A super severe message", e);
    }

    @Test
    public void shouldLogCustomLowLevelToTrace() {
        LogEventFactory.getInstance().setLevel(logger.getName(), Level.TRACE);
        java.util.logging.Level extremelyFine = new java.util.logging.Level("MINISCULE", java.util.logging.Level.FINEST.intValue()-100) {

        };
        logger.log(extremelyFine, "A very very minor message");
        rule.assertContainsMessage(Level.TRACE, "A very very minor message");
    }

    @Test
    public void shouldInheritLogLevel() {
        LogEventFactory.getInstance().setLevel("org.example", Level.INFO);
        String loggerName = "org.example.MyExampleOrg";
        Logger logger = Logger.getLogger(loggerName);
        logger.log(java.util.logging.Level.FINE, "Should not be logged");
        rule.assertNoMessages();
        LogEventFactory.getInstance().setLevel("org.example", Level.DEBUG);
        logger.log(java.util.logging.Level.FINE, "Should be logged", "");

        LogEvent event = rule.getSingleEvent(Level.DEBUG);
        Assert.assertEquals("Should be logged", event.getMessage());
        assertEquals(loggerName, event.getLoggerName());
    }

}
