package org.logevents;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.optional.junit.LogEventRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertTrue;

public class LogEventRuleTest {

    private final Logger logger = LoggerFactory.getLogger("com.example.application.Service");

    @Rule
    public LogEventRule logEventRule = new LogEventRule(Level.DEBUG, "com.example");

    @Test
    public void shouldCaptureSingleLogEvent() {
        logger.debug("Hello world");
        logEventRule.assertSingleMessage(Level.DEBUG, "Hello world");
    }

    @Test
    public void shouldCaptureMultipleLogEvent() {
        logger.debug("Not this one");
        logger.debug("Hello world");
        logger.info("Hello world");
        logEventRule.assertContainsMessage(Level.DEBUG, "Hello world");
    }

    @Test
    public void shouldFailOnMissingLogEvent() {
        logger.debug("Not this one");
        try {
            logEventRule.assertContainsMessage(Level.DEBUG, "Hello world");
            fail("Expected error");
        } catch (AssertionError e) {
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("Could not find <Hello world"));
        }
    }

    @Test
    public void shouldSuppressLogEvent() {
        logger.error("Even though this is an error event, it is not displayed");
    }

    @Test
    public void shouldNotCollectEventsBelowThreshold() {
        logger.trace("Even though this is an error event, it is not displayed");
        logEventRule.assertNoMessages();
        logEventRule.assertDoesNotContainMessage("Even though this is an error event, it is not displayed");
    }

    @Test
    public void shouldChangeCaptureLevel() {
        logEventRule.setLevel(Level.TRACE);
        logger.trace("Trace message - SHOULD be logged");
        logEventRule.assertSingleMessage(Level.TRACE, "Trace message - SHOULD be logged");
    }

    @Test
    public void shouldReportFailureOnAssertDoesNotContainMessage() {
        logger.error("This error WAS expected");
        logEventRule.assertDoesNotContainMessage("This error was not expected");

        logger.error("This error was not expected");
        boolean threwException = true;
        try {
            logEventRule.assertDoesNotContainMessage("This error was not expected");
            threwException = false;
        } catch (AssertionError e) {
            assertTrue(e.toString(), e.toString().contains("Did not expect to find "));
        }
        assertTrue(threwException);
    }
}
