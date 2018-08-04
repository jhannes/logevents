package org.logevents.destinations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.junit.Test;
import org.logevents.LogEvent;
import org.slf4j.event.Level;

public class PatternLogEventFormatterTest {

    private Instant time = Instant.now();
    private PatternLogEventFormatter formatter = new PatternLogEventFormatter("No patter");
    private LogEvent event = new LogEvent("some.logger.name", Level.INFO, "A message from {} to {}",
            new Object[] { "A", "B" }, time);

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
    public void shouldOutputTime() {
        formatter.setPattern("%date");
        assertEquals(time.toString(), formatter.format(event));

        formatter.setPattern("%date{HH:mm:ss}");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss").format(time.atZone(ZoneId.systemDefault())),
                formatter.format(event));

        formatter.setPattern("%date{  HH:mm:ss, Europe/Vilnius}");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss").format(time.atZone(ZoneId.of("Europe/Vilnius"))),
                formatter.format(event));

        formatter.setPattern("%date{ 'HH:mm:ss,SSSS' }");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss,SSSS").format(time.atZone(ZoneId.systemDefault())),
                formatter.format(event));
    }

    @Test
    public void shouldOutputColors() {
        formatter.setPattern("%cyan( [level=%level] ) %logger");
        assertEquals("\033[36m [level=INFO] \033[m some.logger.name",
                formatter.format(event));
    }

    @Test
    public void shouldReplaceSubstring() {
        formatter.setPattern("%red(%replace(..%logger..){'\\.', '/'})");
        assertEquals("\033[31m//some/logger/name//\033[m",
                formatter.format(event));
    }

    @Test
    public void shouldOutputMessageWithConstant() {
        formatter.setPattern("level: [%6level] logger: (%-20logger) shortLogger: (%-8.10logger)");
        assertEquals("level: [  INFO] logger: (some.logger.name    ) shortLogger: (some.logge)", formatter.format(event));
    }

    @Test
    public void shouldReturnUsableErrorMessageForIncompleteFormats() {
        String pattern = "level: [%red(%6level)] logger: (%.-20logger{132}) shortLogger: (%-8.10logger)";

        for (int i=0; i<pattern.length(); i++) {
            try {
                formatter.setPattern(pattern.substring(0, i));
            } catch (IllegalArgumentException e) {
                assertTrue("Expected message of " + e + " to start with <Unknown conversion word> or <End of string>",
                        e.getMessage().startsWith("Unknown conversion word") || e.getMessage().startsWith("End of string while reading <%"));
                continue;
            }
            formatter.format(event);
        }
    }

    @Test
    public void shouldReturnUsableErrorMessageForNestedExceptions() {
        try {
            formatter.setPattern("%date{foobar}");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected message of " + e + " to contain <date>",
                    e.getMessage().contains("date"));
            assertTrue("Expected message of " + e + " to contain <foobar>",
                    e.getMessage().contains("foobar"));
        }
    }

}
