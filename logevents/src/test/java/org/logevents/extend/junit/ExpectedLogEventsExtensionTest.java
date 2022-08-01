package org.logevents.extend.junit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.logevents.LogEventFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public class ExpectedLogEventsExtensionTest {

    private LogEventFactory factory = new LogEventFactory();

    private Logger logger = factory.getLogger(getClass().getName());

    @RegisterExtension
    public ExpectedLogEventsExtension extension = new ExpectedLogEventsExtension(Level.WARN, factory);

    @Test
    public void shouldSucceedWhenLoggingAsExpected() {
        extension.expectMatch(expect -> expect
                .level(Level.WARN).logger(ExpectedLogEventsExtensionTest.class)
                .pattern("This is a {} test for {}").args("nice", "LogEvents"));

        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        extension.verifyCompletion();
    }

    @Test
    public void shouldMatchExactly() {
        extension.expect(ExpectedLogEventsExtensionTest.class, Level.WARN, "This is a nice test");
        logger.warn("This is a {} test", "nice");
        extension.verifyCompletion();
    }

    @Test
    public void shouldFailWhenNoEventsAreLogged() {
        extension.expectMatch(expect -> expect.level(Level.WARN).logger(getClass()).pattern("Test message"));
        try {
            extension.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            assertThat(e.getMessage(), CoreMatchers.containsString("Test message"));
        }
    }

    @Test
    public void shouldFailWhenNotMatchedExactly() {
        extension.expect(ExpectedLogEventsExtensionTest.class, Level.WARN, "This is a bad test");
        logger.warn("This is a {} test", "nice");
        try {
            extension.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            assertThat(e.getMessage(), CoreMatchers.containsString("This is a nice test"));
        }
    }

    @Test
    public void shouldMatchException() {
        extension.expect(ExpectedLogEventsExtensionTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));

        logger.warn("This is a {} test", "nice", new IOException("Uh oh!"));
        extension.verifyCompletion();
    }

    @Test
    public void shouldFailWhenExceptionDoesntMatch() {
        extension.expect(ExpectedLogEventsExtensionTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));
        logger.warn("This is a {} test", "nice", new IOException("Another message!"));
        AssertionError caughtException = null;
        try {
            extension.verifyCompletion();
        } catch (AssertionError e) {
            caughtException = e;
        }
        assertNotNull(caughtException);
        assertThat(caughtException.getMessage(), CoreMatchers.containsString("Another message!"));
    }

    @Test
    public void shouldFailWhenWrongLoglevel() {
        extension.expectPattern(ExpectedLogEventsExtensionTest.class, Level.WARN, "This is a {} test for {}");

        logger.debug("This is a {} test for {}", "nice", "LogEvents");

        AssertionError caughtException = null;
        try {
            extension.verifyCompletion();
        } catch (AssertionError e) {
            caughtException = e;
        }
        Assertions.assertNotNull(caughtException);
        assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
        assertThat(caughtException.getMessage(), CoreMatchers.containsString(""));
    }

    @Test
    public void shouldFailWhenUnexpectedEvent() {
        logger.warn("This is a {} test for {}", "nice", "LogEvents");

        AssertionError caughtException = null;
        try {
            extension.verifyCompletion();
        } catch (AssertionError e) {
            caughtException = e;
        }
        Assertions.assertNotNull(caughtException);
        assertThat(caughtException.getMessage(), CoreMatchers.containsString("Unexpected"));
        assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
    }

    @Test
    public void shouldAllowUnexpectedEventsIfToldExcplicitly() {
        logger.warn("This is a {} test for {}", "controversal", "the LogEvents author");
        extension.setAllowUnexpectedLogs(true);

        try {
            extension.verifyCompletion();
        } finally {
            extension.setAllowUnexpectedLogs(false);
        }
    }

    @Test
    public void shouldSucceedWhenLoggingAsExpectedEvenIfThereAreUnexpectedEventsIfToldExcplicitly() {
        extension.setAllowUnexpectedLogs(true);
        extension.expectMatch(expect -> expect
                .level(Level.WARN).logger(ExpectedLogEventsExtensionTest.class)
                .pattern("This is a {} test for {}").args("nice", "LogEvents"));

        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        logger.warn("This is a {} test for {}", "unexpected", "LogEvents");

        try {
            extension.verifyCompletion();
        } finally {
            extension.setAllowUnexpectedLogs(false);
        }
    }

    @Test
    public void shouldIgnoreUnmatchedEventsBelowThreshold() {
        logger.debug("This is a {} test for {}", "nice", "LogEvents");
        extension.verifyCompletion();
    }
}
