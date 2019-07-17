package org.logevents.extend.junit;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEventFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;

import static org.junit.Assert.fail;

public class ExpectedLogEventsRuleTest {

    private LogEventFactory factory = new LogEventFactory();

    private Logger logger = factory.getLogger(getClass().getName());

    @Rule
    public ExpectedLogEventsRule rule = new ExpectedLogEventsRule(Level.WARN, factory);

    @Test
    public void shouldSucceedWhenLoggingAsExpected() {
        rule.expectMatch(expect -> expect
                .level(Level.WARN).logger(ExpectedLogEventsRuleTest.class)
                .pattern("This is a {} test for {}").args("nice", "LogEvents"));

        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        rule.verifyCompletion();
    }

    @Test
    public void shouldMatchExactly() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a nice test");
        logger.warn("This is a {} test", "nice");
        rule.verifyCompletion();
    }

    @Test
    public void shouldFailWhenNoEventsAreLogged() {
        rule.expectMatch(expect -> expect.level(Level.WARN).logger(getClass()).pattern("Test message"));
        try {
            rule.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            Assert.assertThat(e.getMessage(), CoreMatchers.containsString("Test message"));
        }
    }

    @Test
    public void shouldFailWhenNotMatchedExactly() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a bad test");
        logger.warn("This is a {} test", "nice");
        try {
            rule.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            Assert.assertThat(e.getMessage(), CoreMatchers.containsString("This is a nice test"));
        }
    }

    @Test
    public void shouldMatchException() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));

        logger.warn("This is a {} test", "nice", new IOException("Uh oh!"));
        rule.verifyCompletion();
    }

    @Test
    public void shouldFailWhenExceptionDoesntMatch() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));
        logger.warn("This is a {} test", "nice", new IOException("Another message!"));
        AssertionError caughtException = null;
        try {
            rule.verifyCompletion();
        } catch (AssertionError e) {
            caughtException = e;
        }
        Assert.assertNotNull(caughtException);
        Assert.assertThat(caughtException.getMessage(), CoreMatchers.containsString("Another message!"));
    }

    @Test
    public void shouldFailWhenWrongLoglevel() {
        rule.expectPattern(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a {} test for {}");

        logger.debug("This is a {} test for {}", "nice", "LogEvents");

        AssertionError caughtException = null;
        try {
            rule.verifyCompletion();
        } catch (AssertionError e) {
            caughtException = e;
        }
        Assert.assertNotNull(caughtException);
        Assert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
        Assert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(""));
    }

    @Test
    public void shouldFailWhenUnexpectedEvent() {
        logger.warn("This is a {} test for {}", "nice", "LogEvents");

        AssertionError caughtException = null;
        try {
            rule.verifyCompletion();
        } catch (AssertionError e) {
            caughtException = e;
        }
        Assert.assertNotNull(caughtException);
        Assert.assertThat(caughtException.getMessage(), CoreMatchers.containsString("Unexpected"));
        Assert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
    }

    @Test
    public void shouldIgnoreUnmatchedEventsBelowThreshold() {
        logger.debug("This is a {} test for {}", "nice", "LogEvents");
        rule.verifyCompletion();
    }
}
