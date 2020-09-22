package org.logevents.observers;

import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.formatting.ConsoleFormatting;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.slf4j.event.Level;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleLogEventObserverTest {

    protected ConsoleFormatting format = ConsoleFormatting.getInstance();
    private final ConsoleLogEventFormatter formatter = new ConsoleLogEventFormatter();
    private final String loggerName = "com.example.LoggerName";
    private final Instant time = ZonedDateTime.of(2018, 8, 1, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant();

    @Test
    public void shouldLogMessage() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogEventObserver observer = new ConsoleLogEventObserver(formatter, new PrintStream(buffer));
        observer.logEvent(new LogEventSampler()
                .withLevel(Level.INFO)
                .withTime(time)
                .withThread("main")
                .withLoggerName(loggerName)
                .withFormat("Hello {}").withArgs("there")
                .build());
        String message = new String(buffer.toByteArray());
        assertEquals("10:00:00.000 [main] [\033[34mINFO \033[m] [\033[1;mConsoleLogEventObserverTest.shouldLogMessage(ConsoleLogEventObserverTest.java:38)\033[m]: Hello \033[4;mthere\033[m\n",
                message);
    }

    @Test
    public void shouldColorLogLevel() {
        assertEquals(format.boldRed("ERROR"), formatter.colorizedLevel(Level.ERROR));
        assertEquals(format.red("WARN "), formatter.colorizedLevel(Level.WARN));
        assertEquals(format.blue("INFO "), formatter.colorizedLevel(Level.INFO));
        assertEquals("DEBUG", formatter.colorizedLevel(Level.DEBUG));
    }

    @Test
    public void shouldTurnOffAnsiLogging() {
        Properties properties = new Properties();
        properties.put("observer.console.color", "false");
        formatter.configure(new Configuration(properties, "observer.console"));

        String message = formatter.apply(new LogEventSampler()
                .withLevel(Level.INFO)
                .withTime(time)
                .withThread("main")
                .withLoggerName(loggerName)
                .withFormat("Test")
                .build());
        assertEquals("10:00:00.000 [main] [INFO ] [ConsoleLogEventObserverTest.shouldTurnOffAnsiLogging(ConsoleLogEventObserverTest.java:64)]: Test\n",
                message);
    }

    @Test
    public void shouldDisplayMdc() {
        Properties properties = new Properties();
        properties.put("observer.console.includedMdcKeys", "operation,user");
        Configuration configuration = new Configuration(properties, "observer.console");
        formatter.configure(configuration);
        configuration.checkForUnknownFields();

        String message = formatter.apply(new LogEventSampler()
                .withMdc("operation", "op13")
                .withMdc("user", "userOne")
                .withMdc("secret", "secret value")
                .build());

        assertContains("{operation=op13, user=userOne}", message);
        assertDoesNotContain("secret value", message);
    }

    @Test
    public void shouldIncludeAllMdcKeysByDefault() {
        formatter.configure(new Configuration(new Properties(), "observer.console"));
        String message = formatter.apply(new LogEventSampler()
                .withMdc("op", "read")
                .withMdc("uid", "userFive")
                .build());
        assertContains("{op=read, uid=userFive}", message);
    }

    private void assertContains(String substring, String fullString) {
        assertTrue("Should find <" + substring + "> in <" + fullString + ">",
                fullString.contains(substring));
    }

    private void assertDoesNotContain(String substring, String fullString) {
        assertFalse("Should NOT find <" + substring + "> in <" + fullString + ">",
                fullString.contains(substring));
    }
}
