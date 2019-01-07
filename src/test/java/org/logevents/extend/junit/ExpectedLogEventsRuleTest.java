package org.logevents.extend.junit;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class ExpectedLogEventsRuleTest {

    @Rule
    public ExpectedLogEventsRule rule = new ExpectedLogEventsRule();


    @Test
    public void shouldSucceedWhenLoggingAsExpected() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a {} test for {}");

        LoggerFactory.getLogger(getClass()).warn("This is a {} test for {}", "nice", "LogEvents");
        rule.verifyCompletion();
    }

    @Test
    public void shouldFailWhenWrongLoglevel() {
        rule.expect(ExpectedLogEventsRuleTest.class, Level.WARN, "This is a {} test for {}");

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
}
