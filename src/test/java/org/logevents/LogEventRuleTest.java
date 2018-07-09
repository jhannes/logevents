package org.logevents;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.LogEventRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class LogEventRuleTest {

    private Logger logger = LoggerFactory.getLogger("com.example.application.Service");

    @Rule
    public LogEventRule logEventRule = new LogEventRule("com.example", Level.DEBUG);

    @Test
    public void shouldCaptureLogEvent() {
        logger.debug("Hello world");
        logEventRule.assertSingleMessage("Hello world", Level.DEBUG);
    }

    @Test
    public void shouldSuppressLogEvent() {
        logger.error("Even though this is an error event, it is not displayed");
    }
}
