package org.logevents.optional.junit5;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.logevents.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LogEventExtensionTest {

    private final Logger logger = LoggerFactory.getLogger("com.example.application.Service");

    @RegisterExtension
    public LogEventExtension logEventExtension = new LogEventExtension(Level.DEBUG, "com.example");

    @Test
    public void shouldReportAsRunningInTest() {
        assertTrue(Configuration.isRunningInTest());
    }

    @Test
    public void shouldCaptureSingleLogEvent() {
        logger.debug("Hello world");
        logEventExtension.assertSingleMessage(Level.DEBUG, "Hello world");
    }

    @Test
    public void shouldMatchEventOnPattern() {
        logger.debug("Hello {}", "there");
        logEventExtension.assertContainsMessagePattern(Level.DEBUG, "Hello {}");
    }

    @Test
    public void shouldCaptureMultipleLogEvent() {
        logger.debug("Not this one");
        logger.debug("Hello world");
        logger.info("Hello world");
        logEventExtension.assertContainsMessage(Level.DEBUG, "Hello world");
    }

    @Test
    public void shouldFailOnMissingLogEvent() {
        logger.debug("Not this one");
        try {
            logEventExtension.assertContainsMessage(Level.DEBUG, "Hello world");
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
        logEventExtension.assertNoMessages();
        logEventExtension.assertDoesNotContainMessage("Even though this is an error event, it is not displayed");
    }

    @Test
    public void shouldChangeCaptureLevel() {
        logEventExtension.setLevel(Level.TRACE);
        logger.trace("Trace message - SHOULD be logged");
        logEventExtension.assertSingleMessage(Level.TRACE, "Trace message - SHOULD be logged");
    }

    @Test
    public void shouldReportFailureOnAssertDoesNotContainMessage() {
        logger.error("This error WAS expected");
        logEventExtension.assertDoesNotContainMessage("This error was not expected");

        logger.error("This error was not expected");
        boolean threwException = true;
        try {
            logEventExtension.assertDoesNotContainMessage("This error was not expected");
            threwException = false;
        } catch (AssertionError e) {
            assertTrue(e.toString().contains("Did not expect to find "));
        }
        assertTrue(threwException);
    }

    @Test
    public void shouldReportFailureWhenMessageIsLoggedAboveThreshold() {
        logger.warn("Something went wrong");
        logEventExtension.assertNoMessages(Level.ERROR);
        try {
            logEventExtension.assertNoMessages(Level.WARN);
            fail("Expected exception");
        } catch (AssertionError e) {
            assertTrue(e.toString().contains("Expected no log messages to com.example at level WARN"));
        }
    }

    @Test
    public void shouldFindMatchingMessage() {
        logger.info("Something happened", new IOException("Whoa!"));
        logEventExtension.assertContainsMessage(Level.INFO, "Something happened", new IOException("Whoa!"));
        logEventExtension.clear();
        logger.info("Nothing much happening");
        try {
            logEventExtension.assertContainsMessage(Level.INFO, "Something happened", new IOException("Whoa!"));
            fail("Expected exception");
        } catch (AssertionError e) {
            assertTrue(e.toString().contains("Could not find <Something happened> in logged messages"));
        }
    }
}
