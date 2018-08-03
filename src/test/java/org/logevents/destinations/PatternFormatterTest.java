package org.logevents.destinations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.logevents.LogEvent;
import org.slf4j.event.Level;

public class PatternFormatterTest {

    private PatternLogEventFormatter formatter = new PatternLogEventFormatter();
    private LogEvent event = new LogEvent("some.logger.name", Level.INFO, "A message from {} to {}", "A", "B");

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectIllegalConvertion() {
        formatter.setPattern("%nosuchconversion");
        formatter.format(event);
    }

    @Test
    public void shouldOutputLogger() {
        formatter.setPattern("%logger");
        assertEquals("some.logger.name", formatter.format(event));
    }

    @Test
    public void shouldOutputLevel() {
        formatter.setPattern("%level");
        assertEquals("INFO", formatter.format(event));
    }

    @Test
    public void shouldOutputMessage() {
        formatter.setPattern("message");
        assertEquals("message", formatter.format(event));
    }

    @Test
    public void shouldOutputMessageWithConstant() {
        formatter.setPattern("level: [%6level] logger: (%-20logger) shortLogger: (%-8.10logger)");
        assertEquals("level: [  INFO] logger: (some.logger.name    ) shortLogger: (some.logge)", formatter.format(event));
    }

}
