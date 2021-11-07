package org.logevents.formatting;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.config.Configuration;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PatternLogEventFormatterTest {

    private final Instant time = Instant.ofEpochMilli(1535056492088L);
    private final Map<String, String> properties = new HashMap<>();
    {
        properties.put("observer.foo.formatter.pattern", "No pattern");
    }
    private final Configuration configuration = new Configuration(properties, "observer.foo.formatter");
    private PatternLogEventFormatter formatter = new PatternLogEventFormatter(configuration);
    private final LogEvent event = new LogEventSampler()
        .withLevel(Level.INFO)
        .withLoggerName("some.logger.name")
        .withTime(time)
        .withMarker()
        .withFormat("A messages from {} to {}")
        .withArgs("A", "B")
        .build();
    private final ConsoleFormatting formatting = ConsoleFormatting.getInstance();

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectIllegalConversion() {
        formatter.setPattern("%nosuchconversion");
        formatter.apply(event);
    }

    @Test
    public void shouldOutputLogger() {
        formatter.setPattern("%logger");
        assertEquals("some.logger.name\n", formatter.apply(event));
    }

    @Test
    public void shouldOutputLevel() {
        formatter.setPattern("%level");
        assertEquals("INFO\n", formatter.apply(event));
    }

    @Test
    public void shouldOutputTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"));

        formatter.setPattern("%date");
        assertEquals("2018-08-23 22:34:52.088\n", formatter.apply(event));

        formatter.setPattern("%date{HH:mm:ss}");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss").format(time.atZone(ZoneId.systemDefault())) + "\n",
                formatter.apply(event));

        formatter.setPattern("%date{  HH:mm:ss, Europe/Vilnius}");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss").format(time.atZone(ZoneId.of("Europe/Vilnius"))) + "\n",
                formatter.apply(event));

        formatter.setPattern("%date{ 'HH:mm:ss,SSSS' }");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss,SSSS").format(time.atZone(ZoneId.systemDefault())) + "\n",
                formatter.apply(event));

        formatter.setPattern("%time");
        assertEquals(DateTimeFormatter.ofPattern("HH:mm:ss.SSS").format(time.atZone(ZoneId.systemDefault())) + "\n",
                formatter.apply(event));
    }

    @Test
    public void shouldOutputColors() {
        formatter.setPattern("%cyan( [level=%level] ) %underline(%marker) %logger");
        String marker = event.getMarker().getName();
        assertEquals("\033[36m [level=INFO] \033[m \033[4;m" + marker + "\033[m some.logger.name\n",
                formatter.apply(event));
    }

    @Test
    public void shouldReplaceSubstring() {
        formatter.setPattern("%red(%replace(..%logger..){'\\.', '/'})");
        assertEquals("\033[31m//some/logger/name//\033[m\n",
                formatter.apply(event));
    }

    @Test
    public void shouldOutputMessageWithConstant() {
        formatter.setPattern("level: [%6level] logger: (%-20logger) shortLogger: (%-8.10logger)");
        assertEquals("level: [  INFO] logger: (some.logger.name    ) shortLogger: (some.logge)\n", formatter.apply(event));
    }

    @Test
    public void shouldOutputLocation() {
        Logger logger = LogEventFactory.getInstance().getLogger(getClass().getName());
        CircularBufferLogEventObserver buffer = new CircularBufferLogEventObserver();
        LogEventFactory.getInstance().setObserver(logger, buffer, false);

        logger.warn("Test message");
        LogEvent event = buffer.getEvents().get(0);

        formatter.setPattern("%file:%line - %class#%method");
        assertEquals("FrameworkMethod.java:59 - org.junit.runners.model.FrameworkMethod$1#runReflectiveCall\n",
                formatter.apply(event));
    }

    @Test
    public void shouldHighlightMessage() {
        LogEventSampler sampler = new LogEventSampler();
        LogEvent errorEvent = sampler.withLevel(Level.ERROR).build();
        LogEvent warnEvent = sampler.withLevel(Level.WARN).build();
        LogEvent infoEvent = sampler.withLevel(Level.INFO).build();
        LogEvent debugEvent = sampler.withLevel(Level.DEBUG).build();

        formatter.setPattern("%highlight(%thread)");
        assertEquals(formatting.boldRed(errorEvent.getThreadName()) + "\n", formatter.apply(errorEvent));
        assertEquals(formatting.red(warnEvent.getThreadName()) + "\n", formatter.apply(warnEvent));
        assertEquals(formatting.blue(infoEvent.getThreadName()) + "\n", formatter.apply(infoEvent));
        assertEquals(debugEvent.getThreadName() + "\n", formatter.apply(debugEvent));
    }

    @Test
    public void shouldOutputMdc() {
        LogEvent event = new LogEventSampler().withMdc("user", "Super User")
                .withMdc("role", "admin")
                .build();

        formatter.setPattern("%boldGreen(role=%mdc{role})");
        assertEquals(formatting.boldGreen("role=admin") + "\n", formatter.apply(event));

        formatter.setPattern("[%mdc{userid}]");
        assertEquals("[]\n", formatter.apply(event));

        formatter.setPattern("[%mdc{userid:-no user given}]");
        assertEquals("[no user given]\n", formatter.apply(event));

        formatter.setPattern("%mdc");
        assertEquals(" {user=Super User, role=admin}\n", formatter.apply(event));
    }

    @Test
    public void shouldFilterMdc() {
        properties.put("observer.console.formatter.includedMdcKeys", "user, operation");
        properties.put("observer.console.formatter.pattern", "%mdc");
        formatter = new PatternLogEventFormatter(properties, "observer.console.formatter");

        LogEvent event = new LogEventSampler().withMdc("user", "Super User")
                .withMdc("role", "admin")
                .build();
        assertEquals(" {user=Super User}\n", formatter.apply(event));
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
                    formatter.apply(event).matches("\033\\[(\\d+;)?\\d+mINFO\033\\[m\n"));
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
        formatter.setPattern("%msg");
        IllegalArgumentException ex = createException();
        LogEvent event = new LogEventSampler().withThrowable(ex).build();

        String[] lines = formatter.apply(event).split("\r?\n");
        assertEquals(event.getMessage(), lines[0]);
        assertEquals(ex.toString(), lines[1]);
        assertEquals("\tat org.logevents.formatting.PatternLogEventFormatterTest.createException(PatternLogEventFormatterTest.java:229)",
                lines[2]);
        assertEquals("\tat org.logevents.formatting.PatternLogEventFormatterTest.shouldIncludeExceptionByDefault(PatternLogEventFormatterTest.java:235)",
                lines[3]);
    }

    @Test
    public void shouldSpecifyStackLength() {
        formatter.setPattern("%msg");
        formatter.getExceptionFormatter().ifPresent(f -> f.setMaxLength(2));
        LogEvent event = new LogEventSampler().withThrowable(createException()).build();

        String[] lines = formatter.apply(event).split("\r?\n");
        assertEquals(2+2, lines.length);
        assertEquals(event.getMessage(), lines[0]);
        assertEquals("\tat org.logevents.formatting.PatternLogEventFormatterTest.createException(PatternLogEventFormatterTest.java:229)",
                lines[2]);
    }

    @Test
    public void shouldIncludeApplicationAndNodeInformation() {
        formatter.setPattern("%application %node");
        String message = formatter.apply(new LogEventSampler().build());
        assertEquals(new Configuration().getApplicationName() + " " + new Configuration().getNodeName() + "\n",
                message);
    }

    @Test
    public void shouldOverrideApplicationAndNodeInformation() {
        properties.put("observer.console.formatter.pattern", "%application@%node");
        properties.put("observer.*.nodeName", "my-node");
        properties.put("observer.*.applicationName", "my-app");

        formatter = new PatternLogEventFormatter(properties, "observer.console.formatter");
        assertEquals("my-app@my-node\n", formatter.apply(new LogEventSampler().build()));
        formatter.setPattern("%applicationNode");
        assertEquals("my-app@my-node\n", formatter.apply(new LogEventSampler().build()));
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
