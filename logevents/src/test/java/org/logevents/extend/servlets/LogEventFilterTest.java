package org.logevents.extend.servlets;

import org.junit.Test;
import org.logevents.LogEvent;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogEventFilterTest {

    private Map<Level, List<LogEvent>> logsByLevel = new HashMap<>();

    @Test
    public void shouldFilterByThread() {
        LogEvent matchingEvent = new LogEventSampler().build();
        LogEvent nonMatchingEvent = new LogEventSampler().build();

        LogEventFilter filter = new LogEventFilter(parameters("thread",
                "none", matchingEvent.getThreadName(), "nonsense"));
        assertMatches(matchingEvent, filter);
        assertDoesNotMatch(nonMatchingEvent, filter);
    }

    @Test
    public void shouldFilterByMarkers() {
        LogEvent matchingEvent = new LogEventSampler()
                .withMarker(MarkerFactory.getMarker("MY_MARKER"))
                .build();
        LogEvent nonMatchingEvent = new LogEventSampler()
                .withMarker(MarkerFactory.getMarker("OTHER_MARKER"))
                .build();
        LogEvent eventWithNoMarker = new LogEventSampler()
                .withMarker(null)
                .build();

        LogEventFilter filter = new LogEventFilter(parameters("marker",
                "none", matchingEvent.getMarker().getName(), "nonsense"));
        assertMatches(matchingEvent, filter);
        assertDoesNotMatch(nonMatchingEvent, filter);
        assertDoesNotMatch(eventWithNoMarker, filter);
    }

    @Test
    public void filterByMdc() {
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("mdc[user]", new String[] { "adminUser", "limitedUser" });
        parameters.put("mdc[operation]", new String[]{ "add", "remove" });
        LogEventFilter filter = new LogEventFilter(parameters);

        assertDoesNotMatch(new LogEventSampler().build(), filter);
        try (
                MDC.MDCCloseable ignored = MDC.putCloseable("user", "adminUser");
                MDC.MDCCloseable ignored2 = MDC.putCloseable("operation", "remove")
        ) {
            assertMatches(new LogEventSampler().build(), filter);
        }
        try (
                MDC.MDCCloseable ignored = MDC.putCloseable("user", "randomUser");
                MDC.MDCCloseable ignored2 = MDC.putCloseable("operation", "remove")
        ) {
            assertDoesNotMatch(new LogEventSampler().build(), filter);
        }
        try (
                MDC.MDCCloseable ignored = MDC.putCloseable("user", "adminUser")
        ) {
            assertDoesNotMatch(new LogEventSampler().build(), filter);
        }
    }

    @Test
    public void shouldCollectByLevel() {
        LogEvent traceMessage = record(new LogEventSampler().withLevel(Level.TRACE).build());
        LogEvent debugMessage = record(new LogEventSampler().withLevel(Level.DEBUG).build());
        LogEvent infoMessage = record(new LogEventSampler().withLevel(Level.INFO).build());
        LogEvent warnMessage = record(new LogEventSampler().withLevel(Level.WARN).build());
        LogEvent errorMessage = record(new LogEventSampler().withLevel(Level.ERROR).build());

        LogEventFilter filter = new LogEventFilter(parameters("level", "DEBUG"));

        assertCollectDoesNotContain(traceMessage, filter);
        assertCollectIncludes(debugMessage, filter);
        assertCollectIncludes(infoMessage, filter);
    }

    @Test
    public void shouldSortMessages() {
        ZonedDateTime start = ZonedDateTime.now();
        LogEvent earliestMessage = record(new LogEventSampler()
                .withLevel(Level.ERROR)
                .withTime(start.minusMinutes(9))
                .build());
        LogEvent earlyMessage = record(new LogEventSampler()
                .withLevel(Level.WARN)
                .withTime(start.minusMinutes(7))
                .build());
        LogEvent lateMessage = record(new LogEventSampler()
                .withLevel(Level.ERROR)
                .withTime(start.minusMinutes(5))
                .build());
        LogEvent latestMessage = record(new LogEventSampler()
                .withLevel(Level.INFO)
                .withTime(start.minusMinutes(2))
                .build());

        Collection<LocalTime> logEvents = new LogEventFilter(new HashMap<>()).collectMessages(logsByLevel)
                .stream().map(LogEvent::getLocalTime).collect(Collectors.toList());

        assertEquals(
                Arrays.asList(earliestMessage.getLocalTime(), earlyMessage.getLocalTime(), lateMessage.getLocalTime(), latestMessage.getLocalTime()),
                logEvents
        );
    }

    @Test
    public void shouldFilterByTime() {
        ZonedDateTime start = ZonedDateTime.now().withHour(12);
        LogEvent ancientMessage = record(new LogEventSampler()
                .withLevel(Level.ERROR)
                .withTime(start.minusDays(1).minusMinutes(10))
                .build());
        LogEvent earliestMessage = record(new LogEventSampler()
                .withLevel(Level.ERROR)
                .withTime(start.minusMinutes(90))
                .build());
        LogEvent earlyMessage = record(new LogEventSampler()
                .withLevel(Level.WARN)
                .withTime(start.minusMinutes(15))
                .build());
        LogEvent lateMessage = record(new LogEventSampler()
                .withLevel(Level.ERROR)
                .withTime(start.minusMinutes(5))
                .build());
        LogEvent latestMessage = record(new LogEventSampler()
                .withLevel(Level.INFO)
                .withTime(start.minusMinutes(2))
                .build());

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("time", new String[] { start.minusMinutes(10).toLocalTime().toString() });
        parameters.put("interval", new String[] { "PT10M" });

        Collection<Instant> logEvents = new LogEventFilter(parameters)
                .collectMessages(logsByLevel)
                .stream().map(LogEvent::getInstant).collect(Collectors.toList());
        assertEquals(
                Arrays.asList(earlyMessage.getInstant(), lateMessage.getInstant(), latestMessage.getInstant()),
                logEvents
        );

        parameters.put("interval", new String[] { "PT6M" });
        logEvents = new LogEventFilter(parameters)
                .collectMessages(logsByLevel)
                .stream().map(LogEvent::getInstant).collect(Collectors.toList());
        assertEquals(
                Arrays.asList(earlyMessage.getInstant(), lateMessage.getInstant()),
                logEvents
        );
    }

    private LogEvent record(LogEvent event) {
        logsByLevel.computeIfAbsent(event.getLevel(), l -> new ArrayList<>()).add(event);
        return event;
    }

    private void assertCollectIncludes(LogEvent logEvent, LogEventFilter filter) {
        assertTrue("Expect " + filter + " to include " + logEvent,
                filter.collectMessages(logsByLevel).contains(logEvent));
    }

    private void assertCollectDoesNotContain(LogEvent logEvent, LogEventFilter filter) {
        assertFalse("Expect " + filter + " to exclude " + logEvent,
                filter.collectMessages(logsByLevel).contains(logEvent));
    }

    private Map<String, String[]> parameters(String name, String... values) {
        HashMap<String, String[]> result = new HashMap<>();
        result.put("thread", new String[] { "" });
        result.put("marker", new String[] { "" });
        result.put(name, values);
        return result;
    }

    private void assertDoesNotMatch(LogEvent nonMatchingEvent, LogEventFilter filter) {
        assertFalse(nonMatchingEvent + " should not match " + filter, filter.test(nonMatchingEvent));
    }

    private void assertMatches(LogEvent matchingEvent, LogEventFilter filter) {
        assertTrue(matchingEvent + " should match " + filter, filter.test(matchingEvent));
    }

}