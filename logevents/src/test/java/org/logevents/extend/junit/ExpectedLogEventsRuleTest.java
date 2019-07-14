package org.logevents.extend.junit;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;

public class ExpectedLogEventsRuleTest {

    @Rule
    public ExpectedLogEventsRule rule = new ExpectedLogEventsRule(Level.WARN);


    @Test
    public void shouldSucceedWhenLoggingAsExpected() {
        rule.expectPattern(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a {} test for {}");

        LoggerFactory.getLogger(getClass()).warn("This is a {} test for {}", "nice", "LogEvents");
        rule.verifyCompletion();
    }

    @Test
    public void shouldMatchExactly() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a nice test");
        LoggerFactory.getLogger(getClass()).warn("This is a {} test", "nice");
        rule.verifyCompletion();
    }


    @Test
    public void shouldFailWhenNotMatchedExactly() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a bad test");
        LoggerFactory.getLogger(getClass()).warn("This is a {} test", "nice");
        AssertionError caughtException = null;
        try {
            rule.verifyCompletion();
        } catch (AssertionError e) {
            caughtException = e;
        }
        Assert.assertNotNull(caughtException);
        Assert.assertThat(caughtException.getMessage(), CoreMatchers.containsString("This is a nice test"));
    }

    @Test
    public void shouldMatchException() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));

        LoggerFactory.getLogger(getClass()).warn("This is a {} test", "nice", new IOException("Uh oh!"));
        rule.verifyCompletion();
    }

    @Test
    public void shouldFailWhenExceptionDoesntMatch() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));
        LoggerFactory.getLogger(getClass()).warn("This is a {} test", "nice", new IOException("Another message!"));
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

        LoggerFactory.getLogger(getClass()).debug("This is a {} test for {}", "nice", "LogEvents");

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
        LoggerFactory.getLogger(getClass()).warn("This is a {} test for {}", "nice", "LogEvents");

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
        LoggerFactory.getLogger(getClass()).debug("This is a {} test for {}", "nice", "LogEvents");
        rule.verifyCompletion();
    }
}
