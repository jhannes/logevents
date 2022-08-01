package org.logevents;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertTrue;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.logevents.extend.junit.LogEventExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

class LogEventExtensionTest {

    private Logger logger = LoggerFactory.getLogger("com.example.application.Service");

    @RegisterExtension
    public LogEventExtension logEventExtension = new LogEventExtension(Level.DEBUG, "com.example");

    @Test
    public void shouldCaptureSingleLogEvent() {
        logger.debug("Hello world");
        logEventExtension.assertSingleMessage(Level.DEBUG, "Hello world");
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
            Assert.assertThat(e.getMessage(), CoreMatchers.containsString("Could not find <Hello world"));
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
            assertTrue(e.toString(), e.toString().contains("Did not expect to find "));
        }
        assertTrue(threwException);
    }
}
