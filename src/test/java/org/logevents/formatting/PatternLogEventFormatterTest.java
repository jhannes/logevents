package org.logevents.formatting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

public class PatternLogEventFormatterTest {

    private Instant time = Instant.ofEpochMilli(1535056492088L);
    private PatternLogEventFormatter formatter = new PatternLogEventFormatter("No pattern");
    private LogEvent event = new LogEvent("some.logger.name", Level.INFO, time, null, "A message from {} to {}",
            new Object[] { "A", "B" });
    private ConsoleFormatting formatting = ConsoleFormatting.getInstance();

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectIllegalConvertion() {
        formatter.setPattern("%nosuchconversion");
        formatter.apply(event);
    }

    @Test
    public void shouldOutputLogger() {
        formatter.setPattern("%logger");
        assertEquals("some.logger.name", formatter.apply(event));
    }

    @Test
    public void shouldOutputLevel() {
        formatter.setPattern("%level");
        assertEquals("INFO", formatter.apply(event));
    }

    @Test
    public void shouldOutputTime() {
        formatter.setPattern("%date");
        assertEquals("2018-08-23 22:34:52.088", formatter.apply(event));

        formatter.setPattern("%date{HH:mm:ss}");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss").format(time.atZone(ZoneId.systemDefault())),
                formatter.apply(event));

        formatter.setPattern("%date{  HH:mm:ss, Europe/Vilnius}");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss").format(time.atZone(ZoneId.of("Europe/Vilnius"))),
                formatter.apply(event));

        formatter.setPattern("%date{ 'HH:mm:ss,SSSS' }");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss,SSSS").format(time.atZone(ZoneId.systemDefault())),
                formatter.apply(event));
    }

    @Test
    public void shouldOutputColors() {
        formatter.setPattern("%cyan( [level=%level] ) %logger");
        assertEquals("\033[36m [level=INFO] \033[m some.logger.name",
                formatter.apply(event));
    }

    @Test
    public void shouldReplaceSubstring() {
        formatter.setPattern("%red(%replace(..%logger..){'\\.', '/'})");
        assertEquals("\033[31m//some/logger/name//\033[m",
                formatter.apply(event));
    }

    @Test
    public void shouldOutputMessageWithConstant() {
        formatter.setPattern("level: [%6level] logger: (%-20logger) shortLogger: (%-8.10logger)");
        assertEquals("level: [  INFO] logger: (some.logger.name    ) shortLogger: (some.logge)", formatter.apply(event));
    }

    @Test
    public void shouldOutputLocation() {
        Logger logger = LogEventFactory.getInstance().getLogger(getClass().getName());
        CircularBufferLogEventObserver buffer = new CircularBufferLogEventObserver();
        LogEventFactory.getInstance().setObserver(logger, buffer, false);

        logger.warn("Test message");
        LogEvent event = buffer.getEvents().get(0);

        formatter.setPattern("%file:%line - %class#%method");
        assertEquals("PatternLogEventFormatterTest.java:95 - org.logevents.formatting.PatternLogEventFormatterTest#shouldOutputLocation",
                formatter.apply(event));
    }

    @Test
    public void shouldHighlightMessage() {
        LogEvent errorEvent = new LogEvent("some.logger.name", Level.ERROR, "Error message");
        LogEvent warnEvent = new LogEvent("some.logger.name", Level.WARN, "Warning message");
        LogEvent infoEvent = new LogEvent("some.logger.name", Level.INFO, "Info message");
        LogEvent debugEvent = new LogEvent("some.logger.name", Level.DEBUG, "Debug message");

        formatter.setPattern("%highlight(%thread)");
        String s = Thread.currentThread().getName();
        assertEquals(formatting.boldRed(s), formatter.apply(errorEvent));
        assertEquals(formatting.red(s), formatter.apply(warnEvent));
        assertEquals(formatting.blue(s), formatter.apply(infoEvent));
        assertEquals(s, formatter.apply(debugEvent));
    }

    @Test
    public void shouldOutputMdc() {
        MDC.put("user", "Super User");
        MDC.put("role", "admin");
        LogEvent event = new LogEvent("some.logger.name", Level.WARN, "Warning message");

        formatter.setPattern("%boldGreen(role=%mdc{role})");
        assertEquals(formatting.boldGreen("role=admin"), formatter.apply(event));

        formatter.setPattern("[%mdc{userid}]");
        assertEquals("[]", formatter.apply(event));

        formatter.setPattern("[%mdc{userid:-no user given}]");
        assertEquals("[no user given]", formatter.apply(event));

        formatter.setPattern("%mdc");
        assertEquals("user=Super User, role=admin", formatter.apply(event));
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
            formatter.apply(event);
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

    @Test
    public void shouldOutputColorsReasonably() {
        List<String> colors = Arrays.asList(
                "%black", "%red", "%green", "%yellow", "%blue", "%magenta", "%cyan", "%white",
                "%boldBlack", "%boldRed", "%boldGreen", "%boldYellow", "%boldBlue", "%boldMagenta", "%boldCyan", "%boldWhite");

        for (String color : colors) {
                formatter.setPattern(color + "(%level)");
            assertTrue("Strange color output " + formatter.apply(event),
                    formatter.apply(event).matches("\033\\[(\\d+;)?\\d+mINFO\033\\[m"));
        }
    }

    @Test
    public void shouldColorOutputsShouldBeUnique() {
        List<String> colors = Arrays.asList(
                "%black", "%red", "%green", "%yellow", "%blue", "%magenta", "%cyan", "%white",
                "%boldBlack", "%boldRed", "%boldGreen", "%boldYellow", "%boldBlue", "%boldMagenta", "%boldCyan", "%boldWhite");

        List<String> resultingStrings = colors.stream()
            .map(c -> {
                formatter.setPattern(c + "(%level)");
                return formatter.apply(event);
            })
            .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), findDuplicates(resultingStrings));
    }

    private IllegalArgumentException createException() {
        return new IllegalArgumentException("Error message");
    }

    @Test
    public void shouldIncludeExceptionByDefault() {
        formatter.setPattern("%-5level %logger{36} - %msg");
        IllegalArgumentException ex = createException();
        LogEvent event = new LogEvent("some.logger.name", Level.ERROR, "An error happened", ex);

        String[] lines = formatter.apply(event).split("\r?\n");
        assertEquals("ERROR some.logger.name - An error happened", lines[0]);
        assertEquals(ex.toString(), lines[1]);
        assertEquals("\tat org.logevents.formatting.PatternLogEventFormatterTest.createException(PatternLogEventFormatterTest.java:195)",
                lines[2]);
        assertEquals("\tat org.logevents.formatting.PatternLogEventFormatterTest.shouldIncludeExceptionByDefault(PatternLogEventFormatterTest.java:201)",
                lines[3]);
    }

    @Test
    public void shouldSpecifyStackLength() {
        formatter.setPattern("%-5level %logger{36} - %msg");
        formatter.getExceptionFormatter().setMaxLength(2);
        LogEvent event = new LogEvent("some.logger.name", Level.ERROR, "An error happened", createException());

        String[] lines = formatter.apply(event).split("\r?\n");
        assertEquals(2+2, lines.length);
        assertEquals("ERROR some.logger.name - An error happened", lines[0]);
        assertEquals("\tat org.logevents.formatting.PatternLogEventFormatterTest.createException(PatternLogEventFormatterTest.java:195)",
                lines[2]);
    }

    private List<String> findDuplicates(List<String> resultingStrings) {
        Set<String> uniqueResults = new HashSet<>();
        Set<String> duplicatedResults = new HashSet<>();
        for (String result : resultingStrings) {
            if (!uniqueResults.add(result)) {
                duplicatedResults.add(result);
            }
        }
        return new ArrayList<>(duplicatedResults);
    }
}
