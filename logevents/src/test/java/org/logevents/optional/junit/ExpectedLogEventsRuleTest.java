package org.logevents.optional.junit;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEventFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
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
                .pattern("This is a {} test for {}")
                .args("nice", "LogEvents")
                .argument(0, "nice")
                .argument(1, "LogEvents")
        );

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
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("Test message"));
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
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("This is a nice test"));
        }
    }

    @Test
    public void shouldFailWhenArgumentsNotMatched() {
        rule.expectMatch(expect -> expect
                .level(Level.WARN)
                .logger(ExpectedLogEventsRuleTest.class)
                .pattern("This is a {} test for {}")
                .argument(1, "something else")
        );
        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        try {
            rule.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("something else"));
        }
    }

    @Test
    public void shouldFailWhenArgumentsOutOfBound() {
        rule.expectMatch(expect -> expect
                .level(Level.WARN)
                .argument(10, "something else")
        );
        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        try {
            rule.verifyCompletion();
            fail("Expected exception");
        } catch (AssertionError e) {
            MatcherAssert.assertThat(e.getMessage(), CoreMatchers.containsString("something else"));
        }
    }

    @Test
    public void shouldMatchException() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a nice test",
                new IOException("Uh oh!"));
        assertEquals("ExpectedLogEventsRule{matchers=1, events=[]}", rule.toString());

        logger.warn("This is a {} test", "nice", new IOException("Uh oh!"));
        assertEquals("ExpectedLogEventsRule{matchers=1, events=[LogEvent{org.logevents.optional.junit.ExpectedLogEventsRuleTest,WARN,This is a {} test}]}", rule.toString());
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
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString("Another message!"));
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
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(""));
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
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString("Unexpected"));
        MatcherAssert.assertThat(caughtException.getMessage(), CoreMatchers.containsString(getClass().getName()));
    }

    @Test
    public void shouldAllowUnexpectedEventsIfToldExcplicitly() {
        logger.warn("This is a {} test for {}", "controversal", "the LogEvents author");
        rule.setAllowUnexpectedLogs(true);

        try {
            rule.verifyCompletion();
        } finally {
            rule.setAllowUnexpectedLogs(false);
        }
    }

    @Test
    public void shouldSucceedWhenLoggingAsExpectedEvenIfThereAreUnexpectedEventsIfToldExcplicitly() {
        rule.setAllowUnexpectedLogs(true);
        rule.expectMatch(expect -> expect
                .level(Level.WARN).logger(ExpectedLogEventsRuleTest.class)
                .pattern("This is a {} test for {}").args("nice", "LogEvents"));

        logger.warn("This is a {} test for {}", "nice", "LogEvents");
        logger.warn("This is a {} test for {}", "unexpected", "LogEvents");

        try {
            rule.verifyCompletion();
        } finally {
            rule.setAllowUnexpectedLogs(false);
        }
    }

    @Test
    public void shouldIgnoreUnmatchedEventsBelowThreshold() {
        logger.debug("This is a {} test for {}", "nice", "LogEvents");
        rule.verifyCompletion();
    }
}
