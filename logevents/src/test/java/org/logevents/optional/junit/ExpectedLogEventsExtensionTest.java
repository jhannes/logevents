package org.logevents.optional.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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
                .pattern("This is a {} test for {}")
                .args("nice", "LogEvents")
                .argument(0, "nice")
                .argument(1, "LogEvents")
        );

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
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("Test message"));
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
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("This is a nice test"));
        }
    }

    @Test
    public void shouldFailWhenArgumentsNotMatched() {
        extension.expectMatch(expect -> expect
                .level(Level.WARN)
                .logger(ExpectedLogEventsExtensionTest.class)
                .pattern("This is a {} test for {}")
                .argument(1, "something else")
        );
        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        try {
            extension.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("something else"));
        }
    }

    @Test
    public void shouldFailWhenArgumentsOutOfBound() {
        extension.expectMatch(expect -> expect
                .level(Level.WARN)
                .argument(10, "something else")
        );
        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        try {
            extension.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("something else"));
        }
    }

    @Test
    public void shouldMatchException() {
        extension.expect(ExpectedLogEventsExtensionTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));
        assertEquals("ExpectedLogEventsExtension{matchers=1, events=[]}", extension.toString());

        logger.warn("This is a {} test", "nice", new IOException("Uh oh!"));
        assertEquals("ExpectedLogEventsExtension{matchers=1, events=[LogEvent{org.logevents.optional.junit"
                + ".ExpectedLogEventsExtensionTest,WARN,This is a {} test}]}", extension.toString());
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
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString("Another message!"));
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
        assertNotNull(caughtException);
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(""));
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
        assertNotNull(caughtException);
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString("Unexpected"));
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
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
