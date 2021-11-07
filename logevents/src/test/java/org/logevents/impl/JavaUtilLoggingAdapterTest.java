package org.logevents.impl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.extend.junit.LogEventRule;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.logging.Logger;

public class JavaUtilLoggingAdapterTest {

    @Rule
    public LogEventRule rule = new LogEventRule(Level.TRACE, getClass());

    private final Logger logger = Logger.getLogger(getClass().getName());

    @Before
    public void setUp() {
        JavaUtilLoggingAdapter.install(LogEventFactory.getInstance());
    }

    @Test
    public void shouldLogFinestLevelToTrace() {
        logger.setLevel(java.util.logging.Level.FINEST);
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
        java.util.logging.Level extremelyFine = new java.util.logging.Level("MINISCULE", java.util.logging.Level.FINEST.intValue()-100) {

        };
        logger.setLevel(extremelyFine);
        logger.log(extremelyFine, "A very very minor message");
        rule.assertContainsMessage(Level.TRACE, "A very very minor message");
    }
}
