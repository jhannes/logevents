package org.logevents.formatting;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.formatting.ConsoleFormatting;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.slf4j.event.Level;

public class ConsoleLogEventFormatterTest {

    protected ConsoleFormatting format = ConsoleFormatting.getInstance();
    private ConsoleLogEventFormatter formatter = new ConsoleLogEventFormatter();
    private String loggerName = "com.example.LoggerName";

    @Test
    public void shouldLogMessage() {
        Instant time = ZonedDateTime.of(2018, 8, 1, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant();
        String message = formatter.apply(new LogEvent(loggerName, Level.INFO, time, null, "Hello {}", new Object[] { "there" }));
        assertEquals("10:00 [main] [\033[34mINFO \033[m] [\033[1;mcom.example.LoggerName\033[m]: Hello there",
                message);
    }

    @Test
    public void shouldColorLogLevel() {
        assertEquals(format.boldRed("ERROR"), formatter.colorizedLevel(Level.ERROR));
        assertEquals(format.red("WARN "), formatter.colorizedLevel(Level.WARN));
        assertEquals(format.blue("INFO "), formatter.colorizedLevel(Level.INFO));
        assertEquals("DEBUG", formatter.colorizedLevel(Level.DEBUG));
    }
}
