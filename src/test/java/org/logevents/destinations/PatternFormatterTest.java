package org.logevents.destinations;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.junit.Test;
import org.logevents.LogEvent;
import org.slf4j.event.Level;

public class PatternFormatterTest {

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
        assertEquals("\033[41m//some/logger/name//\033[m",
                formatter.format(event));
    }

    @Test
    public void shouldOutputMessageWithConstant() {
        formatter.setPattern("level: [%6level] logger: (%-20logger) shortLogger: (%-8.10logger)");
        assertEquals("level: [  INFO] logger: (some.logger.name    ) shortLogger: (some.logge)", formatter.format(event));
    }

}
