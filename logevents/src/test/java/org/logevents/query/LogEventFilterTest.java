package org.logevents.query;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.LogEventBuffer;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogEventFilterTest {

    private final Marker MY_MARKER = MarkerFactory.getMarker("MY_MARKER");
    private final Marker OTHER_MARKER = MarkerFactory.getMarker("OTHER_MARKER");
    private LogEventBuffer logsByLevel = new LogEventBuffer();

    @Before
    public void setUp() {
        LogEventBuffer.clear();
    }

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
                .withMarker(MY_MARKER)
                .build();
        LogEvent nonMatchingEvent = new LogEventSampler()
                .withMarker(OTHER_MARKER)
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
    public void shouldFilterByLogger() {
        LogEvent matchingEvent = new LogEventSampler().build();
        LogEvent secondMatchingEvent = new LogEventSampler().build();
        LogEvent nonMatchingEvent = new LogEventSampler()
                .withLoggerName(LogEventSampler.sampleLoggerName() + "Foo")
                .build();

        LogEventFilter filter = new LogEventFilter(parameters("logger", matchingEvent.getLoggerName(), secondMatchingEvent.getLoggerName()));
        assertMatches(matchingEvent, filter);
        assertMatches(secondMatchingEvent, filter);
        assertDoesNotMatch(nonMatchingEvent, filter);
    }

    @Test
    public void filterByMdc() {
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("mdc[user]", new String[] { "adminUser", "limitedUser" });
        parameters.put("mdc[operation]", new String[]{ "add", "remove" });
        LogEventFilter filter = new LogEventFilter(parameters);

        assertDoesNotMatch(new LogEventSampler().build(), filter);
        assertMatches(new LogEventSampler().withMdc("user", "adminUser").withMdc("operation", "remove").build(), filter);
        assertDoesNotMatch(new LogEventSampler().withMdc("user", "randomUser").withMdc("operation", "remove").build(), filter);
        assertDoesNotMatch(new LogEventSampler().withMdc("user", "adminUser").build(), filter);
    }

    @Test
    public void shouldCollectByLevel() {
        LogEvent traceMessage = record(new LogEventSampler().withLevel(Level.TRACE).build());
        LogEvent debugMessage = record(new LogEventSampler().withLevel(Level.DEBUG).build());
        LogEvent infoMessage = record(new LogEventSampler().withLevel(Level.INFO).build());
        LogEvent warnMessage = record(new LogEventSampler().withLevel(Level.WARN).build());
        LogEvent errorMessage = record(new LogEventSampler().withLevel(Level.ERROR).build());

        LogEventFilter filter = new LogEventFilter(parameters("level", "DEBUG"));

        assertCollectionDoesNotContain(traceMessage, filter);
        assertCollectionIncludes(debugMessage, filter);
        assertCollectionIncludes(infoMessage, filter);
    }

    @Test
    public void shouldSortMessages() {
        ZonedDateTime start = ZonedDateTime.now();
        LogEvent earliestMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(9))
                .build());
        LogEvent earlyMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(7))
                .build());
        LogEvent lateMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(5))
                .build());
        LogEvent latestMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(2))
                .build());

        Collection<LocalTime> logEvents = logsByLevel.query(new LogEventFilter(new HashMap<>())).getEvents().stream()
                .map(LogEvent::getLocalTime).collect(Collectors.toList());

        assertEquals(
                Arrays.asList(earliestMessage.getLocalTime(), earlyMessage.getLocalTime(), lateMessage.getLocalTime(), latestMessage.getLocalTime()),
                logEvents
        );
    }

    @Test
    public void shouldFilterByTime() {
        ZonedDateTime start = ZonedDateTime.now().withHour(12);
        LogEvent ancientMessage = record(new LogEventSampler()
                .withTime(start.minusDays(1).minusMinutes(10))
                .build());
        LogEvent earliestMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(90))
                .build());
        LogEvent earlyMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(15))
                .build());
        LogEvent lateMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(5))
                .build());
        LogEvent latestMessage = record(new LogEventSampler()
                .withTime(start.minusMinutes(2))
                .build());

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("time", new String[] { start.minusMinutes(10).toLocalTime().toString() });
        parameters.put("interval", new String[] { "PT10M" });

        Collection<Instant> logEvents = logsByLevel.query(new LogEventFilter(parameters)).getEvents().stream().map(LogEvent::getInstant).collect(Collectors.toList());
        assertEquals(
                Arrays.asList(earlyMessage.getInstant(), lateMessage.getInstant(), latestMessage.getInstant()),
                logEvents
        );

        parameters.put("interval", new String[] { "PT6M" });
        logEvents = logsByLevel.query(new LogEventFilter(parameters)).getEvents().stream().map(LogEvent::getInstant).collect(Collectors.toList());
        assertEquals(
                Arrays.asList(earlyMessage.getInstant(), lateMessage.getInstant()),
                logEvents
        );
    }

    @Test
    public void shouldExcludeMessagesOutsideTimeWindowInFacets() {
        LogEvent oldEvent = new LogEventSampler()
                .withThread("OldThread")
                .withTime(Instant.now().minusSeconds(6000)).build();

        record(new LogEventSampler().withThread("main").build());
        record(new LogEventSampler().withThread("main").withTime(Instant.now().minusSeconds(6000)).build());
        record(oldEvent);
        record(new LogEventSampler().withThread("TimeThread-8").build());

        LogEventFilter filter = new LogEventFilter(parameters("interval", "PT1M"));

        LogEventSummary summary = logsByLevel.query(filter).getSummary();
        Map<String, Object> facets = summary.toJson();
        assertEquals(new HashSet<>(Arrays.asList("main", "TimeThread-8")),
                facets.get("threads"));
        assertEquals(2, summary.getRowCount());
        assertEquals(2, summary.getFilteredRowCount());
    }

    @Test
    public void shouldIncludeNonSelectedThreadsInFacets() {
        record(new LogEventSampler().withThread("main").build());
        record(new LogEventSampler().withThread("TimeThread-8").build());

        LogEventFilter filter = new LogEventFilter(parameters("thread", "main"));
        LogEventSummary summary = logsByLevel.query(filter).getSummary();
        Map<String, Object> facets = summary.toJson();
        assertEquals(new HashSet<>(Arrays.asList("main", "TimeThread-8")),
                facets.get("threads"));
        assertEquals(2, summary.getRowCount());
        assertEquals(1, summary.getFilteredRowCount());
    }

    @Test
    public void shouldIncludeMarkersInFacets() {
        record(new LogEventSampler().withMarker(MY_MARKER).build());
        record(new LogEventSampler().withMarker(MY_MARKER).build());
        record(new LogEventSampler().withMarker(OTHER_MARKER).build());

        LogEventFilter filter = new LogEventFilter(parameters("marker", "my_marker"));
        Map<String, Object> facets = logsByLevel.query(filter).getSummary().toJson();
        assertEquals(new HashSet<>(Arrays.asList(MY_MARKER.getName(), OTHER_MARKER.getName())),
                facets.get("markers"));
    }

    @Test
    public void shouldLimitFilter() {
        Marker UNIQUE_MARKER = MarkerFactory.getMarker("Marker-" + UUID.randomUUID());

        record(new LogEventSampler().withMarker(UNIQUE_MARKER).build());
        record(new LogEventSampler().withMarker(UNIQUE_MARKER).build());
        record(new LogEventSampler().withMarker(UNIQUE_MARKER).build());

        HashMap<String, String[]> parameters = new HashMap<>();
        parameters.put("marker", new String[]{UNIQUE_MARKER.getName()});
        parameters.put("limit", new String[]{"2"});
        LogEventQueryResult queryResult = logsByLevel.query(new LogEventFilter(parameters));
        assertEquals(2, queryResult.getEvents().size());
        assertEquals(3, queryResult.getSummary().getRowCount());

        parameters.remove("limit");
        assertEquals(3, logsByLevel.query(new LogEventFilter(parameters)).getEvents().size());
    }

    @Test
    public void shouldIncludeLoggersInFacets() {
        String firstLogger = "com.example.ClassOne";
        String secondLogger = "com.example.ClassTwo";
        record(new LogEventSampler().withLoggerName(firstLogger).build());
        record(new LogEventSampler().withLoggerName(firstLogger).build());
        record(new LogEventSampler().withLoggerName(secondLogger).build());

        LogEventFilter filter = new LogEventFilter(parameters("logger", firstLogger));
        Map<String, Object> facets = logsByLevel.query(filter).getSummary().toJson();
        List<Object> expected = new ArrayList<>();
        Map<String, String> first = new HashMap<>();
        first.put("name", firstLogger);
        first.put("abbreviatedName", "c.e.ClassOne");
        expected.add(first);
        Map<String, String> second = new HashMap<>();
        second.put("name", secondLogger);
        second.put("abbreviatedName", "c.e.ClassTwo");
        expected.add(second);
        assertEquals(expected, facets.get("loggers"));
    }

    @Test
    public void shouldIncludeMdcInFacets() {
        record(new LogEventSampler().withMdc("ip", "127.0.0.1").withMdc("url", "/api/op").build());
        record(new LogEventSampler().withMdc("ip", "127.0.0.1").withMdc("url", null).build());
        record(new LogEventSampler().withMdc("ip", "10.0.0.4").build());

        Map<String, Object> facets = logsByLevel.query(new LogEventFilter(new HashMap<>())).getSummary().toJson();
        List<Map<String, Object>> mdc = (List<Map<String, Object>>) facets.get("mdc");

        Map<String, Object> ipMdc = mdc.stream().filter(m -> m.get("name").equals("ip")).findAny()
                .orElseThrow(() -> new IllegalArgumentException("missing ip in " + mdc));
        Map<String, Object> urlMdc = mdc.stream().filter(m -> m.get("name").equals("url")).findAny()
                .orElseThrow(() -> new IllegalArgumentException("missing ip in " + mdc));

        assertEquals(Arrays.asList("10.0.0.4", "127.0.0.1"), ipMdc.get("values"));
        assertEquals(Arrays.asList("/api/op"), urlMdc.get("values"));
    }

    private LogEvent record(LogEvent event) {
        logsByLevel.logEvent(event);
        return event;
    }

    private void assertCollectionIncludes(LogEvent logEvent, LogEventFilter filter) {
        assertTrue("Expect " + filter + " to include " + logEvent,
                logsByLevel.query(filter).getEvents().contains(logEvent));
    }

    private void assertCollectionDoesNotContain(LogEvent logEvent, LogEventFilter filter) {
        assertFalse("Expect " + filter + " to exclude " + logEvent,
                logsByLevel.query(filter).getEvents().contains(logEvent));
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
