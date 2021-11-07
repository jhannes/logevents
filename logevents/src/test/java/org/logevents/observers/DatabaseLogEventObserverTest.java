package org.logevents.observers;

import junit.framework.AssertionFailedError;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.query.LogEventQuery;
import org.logevents.query.LogEventQueryResult;
import org.logevents.query.LogEventSummary;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.sql.SQLException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseLogEventObserverTest {

    private final Marker MY_MARKER = MarkerFactory.getMarker("MY_MARKER");
    private final Marker OTHER_MARKER = MarkerFactory.getMarker("OTHER_MARKER");

    private final Map<String, String> properties = new HashMap<>();
    {
        properties.put("observer.db.jdbcUrl", "jdbc:h2:mem:logevents-test;DB_CLOSE_DELAY=-1");
        properties.put("observer.db.jdbcUsername", "sa");
        properties.put("observer.db.jdbcPassword", "sa");
        properties.put("observer.db.logEventsTable", "test_log_events");
    }
    private DatabaseLogEventObserver observer = new DatabaseLogEventObserver(properties, "observer.db");

    @Test
    public void shouldListSavedLogEvents() {
        LogEvent event = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(event));
        assertContains(event.getMessage(), listEvents(event), LogEvent::getMessage);
    }

    @Test
    public void shouldSaveAllFields() {
        LogEvent event = new LogEventSampler().withFormat(UUID.randomUUID().toString())
                .withArgs(LogEventSampler.randomString(), LogEventSampler.randomString())
                .withMarker()
                .build();
        observer.processBatch(new LogEventBatch().add(event));

        LogEvent savedEvent = listEvents(event.getLevel(), event.getZonedDateTime(), Duration.ofSeconds(1))
                .stream()
                .filter(e -> e.getMessage().equals(event.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Didn't find <" + event.getMessage() + ">"));
        assertEquals(savedEvent.getLevel(), event.getLevel());
        assertEquals(savedEvent.getLoggerName(), event.getLoggerName());
        assertEquals(savedEvent.getMessage(), event.getMessage());
        assertEquals(savedEvent.getInstant(), event.getInstant());
        assertEquals(savedEvent.getThreadName(), event.getThreadName());
        assertEquals(savedEvent.getMarker(), event.getMarker());
        assertArrayEquals(savedEvent.getArgumentArray(), event.getArgumentArray());
    }

    @Test
    public void shouldSaveEventsWithNullArguments() {
        LogEvent event = new LogEventSampler().withArgs("123", null, "321").build();
        observer.processBatch(new LogEventBatch().add(event));

        LogEvent savedEvent = listEvents(event.getLevel(), event.getZonedDateTime(), Duration.ofSeconds(1))
                .stream().filter(e -> e.getMessage().equals(event.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Didn't find <" + event.getMessage() + ">"));
        assertArrayEquals(event.getArgumentArray(), savedEvent.getArgumentArray());
    }

    @Test
    public void shouldTruncateLongFieldsWhenSaving() {
        String loggerNamePart = "abc12345678901234567890xyz12345678901234567890";
        String veryLongLoggerName = loggerNamePart + "." + loggerNamePart + "." + loggerNamePart + "." + loggerNamePart;
        LogEvent event = new LogEventSampler().withLoggerName(veryLongLoggerName).build();
        observer.processBatch(new LogEventBatch().add(event));

        LogEvent savedEvent = listEvents(event.getLevel(), event.getZonedDateTime(), Duration.ofSeconds(1))
                .stream().filter(e -> e.getMessage().equals(event.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Didn't find <" + event.getMessage() + ">"));
        assertEquals(100, savedEvent.getLoggerName().length());
        assertTrue(veryLongLoggerName.startsWith(savedEvent.getLoggerName()));
    }

    @Test
    public void shouldSaveExceptionInformation() {
        LogEvent event = new LogEventSampler().withThrowable().build();
        observer.processBatch(new LogEventBatch().add(event));

        Map<String, Object> savedEvent = observer
                .query(query(Optional.of(event.getLevel()), event.getZonedDateTime(), Duration.ofSeconds(1)))
                .getEventsAsJson()
                .stream()
                .filter(e -> e.get("formattedMessage").equals(event.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Didn't find <" + event.getMessage() + ">"));

        assertEquals(event.getThrowable().toString(), savedEvent.get("throwable"));
    }

    @Test
    public void shouldSaveApplicationAndNodeName() {
        LogEvent event = new LogEventSampler().withThrowable().build();
        observer.processBatch(new LogEventBatch().add(event));

        Map<String, Object> savedEvent = observer
                .query(query(Optional.of(event.getLevel()), event.getZonedDateTime(), Duration.ofSeconds(1)))
                .getEventsAsJson()
                .stream()
                .filter(e -> e.get("formattedMessage").equals(event.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Didn't find <" + event.getMessage() + ">"));

        assertEquals(new Configuration().getNodeName(), savedEvent.get("node"));
        assertEquals(new Configuration().getApplicationName(), savedEvent.get("application"));
    }

    @Test
    public void shouldFilterOnThreshold() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters;
        parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        assertContains(event.getMessage(), listEvents(parameters), LogEvent::getMessage);

        parameters.put("level", new String[] { Level.WARN.toString() });
        assertDoesNotContain(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
    }

    @Test
    public void shouldFilterOnMarkers() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("marker", new String[] { "NON_EXISTING_MARKER" });
        assertDoesNotContain(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
        parameters.put("marker", new String[] { "NON_EXISTING_MARKER", event.getMarker().getName() });
        assertContains(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
    }

    @Test
    public void shouldExcludeLoggersAndMarkers() {
        String includedLogger = "com.example.foo.FooLogger";
        String excludedLogger = "com.example.foo.BarLogger";
        Marker includedMarker = MY_MARKER;
        Marker excludedMarker = OTHER_MARKER;
        ZonedDateTime logTime = ZonedDateTime.now().minusYears(9);
        LogEventSampler sampler = new LogEventSampler().withTime(logTime);
        LogEvent eventWithIncludedMarkerAndLogger = sampler.withMarker(includedMarker).withLoggerName(includedLogger).build();
        LogEvent eventWithIncludedLogger = sampler.withMarker(null).withLoggerName(includedLogger).build();
        LogEvent eventWithExcludedLogger = sampler.withLoggerName(excludedLogger).build();
        LogEvent eventWithExcludedMarker = sampler.withMarker(excludedMarker).withLoggerName(includedLogger).build();
        observer.processBatch(new LogEventBatch()
                .add(eventWithIncludedMarkerAndLogger)
                .add(eventWithIncludedLogger)
                .add(eventWithExcludedLogger)
                .add(eventWithExcludedMarker));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), logTime, Duration.ofSeconds(10));
        parameters.put("marker", new String[] { excludedMarker.getName() });
        parameters.put("includeMarkers", new String[] { "exclude" });
        parameters.put("logger", new String[] { excludedLogger });
        parameters.put("includeLoggers", new String[] { "exclude" });
        assertContains(eventWithIncludedMarkerAndLogger.getMessage(), listEvents(parameters), LogEvent::getMessage);
        assertContains(eventWithIncludedLogger.getMessage(), listEvents(parameters), LogEvent::getMessage);
        assertDoesNotContain(eventWithExcludedMarker.getMessage(), listEvents(parameters), LogEvent::getMessage);
        assertDoesNotContain(eventWithExcludedLogger.getMessage(), listEvents(parameters), LogEvent::getMessage);
    }


    @Test
    public void shouldFilterOnLoggers() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("logger", new String[] { "org.example.non.Existing" });
        assertDoesNotContain(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
        parameters.put("logger", new String[] { "org.example.non.Existing", event.getLoggerName() });
        assertContains(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
    }

    @Test
    public void shouldFilterOnNodes() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("node", new String[] { "some.example.org" });
        assertDoesNotContain(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
        parameters.put("node", new String[] { "some.example.org", new Configuration().getNodeName() });
        assertContains(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
    }

    @Test
    public void shouldFilterOnApplication() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("application", new String[] { "example-server" });
        assertDoesNotContain(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
        parameters.put("application", new String[] { "example-server", new Configuration().getApplicationName() });
        assertContains(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
    }

    @Test
    public void shouldFilterOnThreads() {
        LogEvent event = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("thread", new String[] { "Thread-nonexisting" });
        assertDoesNotContain(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
        parameters.put("thread", new String[] { "Thread-nonexisting", event.getThreadName() });
        assertContains(event.getMessage(), listEvents(parameters), LogEvent::getMessage);
    }

    @Test
    public void shouldLimitFilter() {
        ZonedDateTime logTime = ZonedDateTime.now().minusSeconds(100 * 24 * 60 * 60);
        LogEventSampler logEventSampler = new LogEventSampler().withTime(logTime);
        observer.processBatch(new LogEventBatch().add(logEventSampler.build()));
        observer.processBatch(new LogEventBatch().add(logEventSampler.build()));
        observer.processBatch(new LogEventBatch().add(logEventSampler.build()));
        observer.processBatch(new LogEventBatch().add(logEventSampler.build()));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.DEBUG), logTime, Duration.ofMinutes(1));
        parameters.put("limit", new String[]{"2"});
        LogEventQueryResult result = observer.query(new LogEventQuery(parameters));
        assertEquals(2, listEvents(parameters).size());
        assertEquals(4, result.getSummary().getRowCount());

        properties.put("observer.db.noFetchFirstSupport", "true");
        observer = new DatabaseLogEventObserver(properties, "observer.db");
        result = observer.query(new LogEventQuery(parameters));
        assertEquals(2, result.getEvents().size());
        assertEquals(4, result.getSummary().getRowCount());

        parameters.remove("limit");
        assertEquals(4, listEvents(parameters).size());
    }

    @Test
    public void shouldFilterOnMdc() {
        ZonedDateTime time = ZonedDateTime.now().minusYears(3);
        LogEvent event1 = new LogEventSampler().withTime(time).withMdc("op", "firstOp").build();
        LogEvent event2 = new LogEventSampler().withTime(time).withMdc("op", "secondOp").build();
        LogEvent event3 = new LogEventSampler().withTime(time).withMdc("op", "secondOp").withMdc("ip", "127.0.0.1").build();
        observer.processBatch(new LogEventBatch().add(event1).add(event2).add(event3));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), time, Duration.ofHours(10));
        parameters.put("mdc[ip]", new String[] { "127.0.0.1" });
        Collection<LogEvent> events = listEvents(parameters);
        assertDoesNotContain(event1.getMessage(), events, LogEvent::getMessage);
        assertDoesNotContain(event2.getMessage(), events, LogEvent::getMessage);
        assertContains(event3.getMessage(), events, LogEvent::getMessage);
        parameters.remove("mdc[ip]");

        parameters.put("mdc[op]", new String[] { "secondOp" });
        events = listEvents(parameters);
        assertDoesNotContain(event1.getMessage(), events, LogEvent::getMessage);
        assertContains(event2.getMessage(), events, LogEvent::getMessage);
        assertContains(event3.getMessage(), events, LogEvent::getMessage);

        parameters.put("mdc[op]", new String[] { "firstOp", "secondOp" });
        events = listEvents(parameters);
        assertContains(event1.getMessage(), events, LogEvent::getMessage);
        assertContains(event2.getMessage(), events, LogEvent::getMessage);
        assertContains(event3.getMessage(), events, LogEvent::getMessage);
    }

    @Test
    public void shouldSaveMdc() {
        Map<String, String> mdc = new HashMap<>();
        mdc.put("ip", "127.0.0.1");
        mdc.put("url", "/api/op");
        LogEvent event = new LogEventSampler().withFormat(UUID.randomUUID().toString())
                .withMdc(mdc)
                .build();
        observer.processBatch(new LogEventBatch().add(event));

        LogEvent savedEvent = listEvents(event)
                .stream()
                .filter(e -> e.getMessage().equals(event.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Didn't find <" + event.getMessage() + ">"));
        assertEquals(mdc, savedEvent.getMdcProperties());
    }

    @Test
    public void shouldFilterSavedLogEvents() {
        LogEvent pastEvent = new LogEventSampler()
                .withTime(ZonedDateTime.now().minusDays(1))
                .build();
        observer.logEvent(pastEvent);
        LogEvent currentEvent = new LogEventSampler()
                .withTime(ZonedDateTime.now())
                .build();
        observer.logEvent(currentEvent);
        LogEvent futureEvent = new LogEventSampler()
                .withTime(ZonedDateTime.now().plusDays(1))
                .build();
        observer.processBatch(new LogEventBatch().add(pastEvent).add(currentEvent).add(futureEvent));

        Collection<LogEvent> events = listEvents(Level.TRACE, ZonedDateTime.now(), Duration.ofHours(1));
        assertContains(currentEvent.getMessage(), events, LogEvent::getMessage);
        assertDoesNotContain(pastEvent.getMessage(), events, LogEvent::getMessage);
        assertDoesNotContain(futureEvent.getMessage(), events, LogEvent::getMessage);
    }

    @Test
    public void shouldFilterByLevel() {
        LogEventSampler logEventSampler = new LogEventSampler().withRandomTime();
        LogEvent higherEvent = logEventSampler.withLevel(Level.WARN).build();
        LogEvent matchingEvent = logEventSampler.withLevel(Level.INFO).build();
        LogEvent lowerEvent = logEventSampler.withLevel(Level.DEBUG).build();
        observer.processBatch(new LogEventBatch().add(higherEvent).add(matchingEvent).add(lowerEvent));

        Collection<LogEvent> events = listEvents(Level.INFO, ZonedDateTime.now(), Duration.ofHours(1));
        assertContains(higherEvent.getMessage(), events, LogEvent::getMessage);
        assertContains(matchingEvent.getMessage(), events, LogEvent::getMessage);
        assertDoesNotContain(lowerEvent.getMessage(), events, LogEvent::getMessage);
    }

    @Test
    public void shouldIncludeMdcInFacets() throws SQLException {
        ZonedDateTime now = ZonedDateTime.now().minusWeeks(2);
        LogEventSampler sampler = new LogEventSampler().withTime(now);
        observer.processBatch(new LogEventBatch()
                .add(sampler.withMdc("ip", "127.0.0.1").withMdc("url", "/api/op").build())
                .add(sampler.withMdc("ip", "127.0.0.1").build())
                .add(sampler.withMdc("ip", "10.0.0.4").build()));

        LogEventSummary summary = observer.getSummary(listEvents(Optional.empty(), now, Duration.ofMinutes(10)));
        assertEquals(new HashSet<>(Arrays.asList("ip", "url")), summary.getMdcMap().keySet());
        assertEquals(new HashSet<>(Arrays.asList("127.0.0.1", "10.0.0.4")), summary.getMdcMap().get("ip"));
        assertEquals(new HashSet<>(Arrays.asList("/api/op")), summary.getMdcMap().get("url"));
        assertEquals(3, summary.getFilteredRowCount());
    }

    @Test
    public void shouldIncludeMarkersAndLoggersInFacets() throws SQLException {
        String logger1 = "com.example.foo.FooServer";
        String logger2 = "org.logevents.LogEventSampler";
        ZonedDateTime logTime = ZonedDateTime.now().minusYears(10);
        LogEventSampler sampler = new LogEventSampler().withTime(logTime);
        LogEvent event1 = sampler.withMarker(MY_MARKER).withLoggerName(logger1).build();
        LogEvent event2 = sampler.withMarker(MY_MARKER).withLoggerName(logger1).build();
        LogEvent event3 = sampler.withMarker(OTHER_MARKER).withLoggerName(logger2).build();
        observer.processBatch(new LogEventBatch().add(event1).add(event2).add(event3));

        LogEventSummary summary = observer.getSummary(query(Optional.empty(), logTime, Duration.ofHours(1)));
        assertEquals(new HashSet<>(Arrays.asList(event1.getMarker().getName(), event3.getMarker().getName())),
                summary.getMarkers());
        assertEquals(new HashSet<>(Arrays.asList(event1.getLoggerName(), event2.getLoggerName(), event3.getLoggerName())),
                summary.getLoggers());
        assertEquals(new HashSet<>(Arrays.asList(event1.getThreadName(), event2.getThreadName(), event3.getThreadName())),
                summary.getThreads());
        assertEquals(new HashSet<>(Arrays.asList(new Configuration().getNodeName())), summary.getNodes());
        assertEquals(new HashSet<>(Arrays.asList(new Configuration().getApplicationName())), summary.getApplications());
    }

    @Test
    public void shouldExcludeMessagesOutsideFilterFromFacets() throws SQLException {
        ZonedDateTime time = ZonedDateTime.now().minusYears(2);
        Duration interval = Duration.ofMinutes(10);

        LogEvent event1 = new LogEventSampler().withMarker(MY_MARKER)
                .withMdc("mdcKey", "too old")
                .withTime(time.minus(interval).minusMinutes(1)).build();
        LogEvent event2 = new LogEventSampler().withMarker(MY_MARKER)
                .withTime(time.minus(interval).plusMinutes(1))
                .withMdc("mdcKey", "too boring")
                .withLevel(Level.INFO).build();
        LogEvent event3 = new LogEventSampler().withMarker(OTHER_MARKER)
                .withMdc("mdcKey", "just right")
                .withTime(time.minus(interval).plusMinutes(1))
                .withLevel(Level.WARN).build();
        observer.processBatch(new LogEventBatch().add(event1).add(event2).add(event3));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.WARN), time, interval);
        parameters.put("marker", new String[] { "MY_MARKER" });
        LogEventSummary summary = observer.getSummary(new LogEventQuery(parameters));
        assertEquals(new HashSet<>(Arrays.asList("mdcKey")), summary.getMdcMap().keySet());
        assertEquals(new HashSet<>(Arrays.asList("just right")), summary.getMdcMap().get("mdcKey"));
        assertEquals(new HashSet<>(Arrays.asList(OTHER_MARKER.toString())), summary.getMarkers());
        assertEquals(1, summary.getRowCount());
        assertEquals(0, summary.getFilteredRowCount());
    }

    private Collection<LogEvent> listEvents(Level level, ZonedDateTime zonedDateTime, Duration duration) {
        return observer.query(query(Optional.of(level), zonedDateTime, duration)).getEvents();
    }

    private Collection<LogEvent> listEvents(HashMap<String, String[]> parameters) {
        return observer.query(new LogEventQuery(parameters)).getEvents();
    }

    private Collection<LogEvent> listEvents(LogEvent event) {
        return listEvents(event.getLevel(), event.getZonedDateTime(), Duration.ofSeconds(1));
    }

    private LogEventQuery listEvents(Optional<Level> empty, ZonedDateTime now, Duration duration) {
        return query(empty, now, duration);
    }

    private LogEventQuery query(Optional<Level> level, ZonedDateTime time, Duration interval) {
        HashMap<String, String[]> untypedParameters = parameters(level, time, interval);
        return new LogEventQuery(untypedParameters);
    }

    private HashMap<String, String[]> parameters(Optional<Level> level, ZonedDateTime time, Duration interval) {
        HashMap<String, String[]> untypedParameters = new HashMap<>();
        level.ifPresent(l -> untypedParameters.put("level", new String[] { l.toString() }));
        untypedParameters.put("time", new String[] { time.toLocalTime().toString() });
        untypedParameters.put("date", new String[] { time.toLocalDate().toString() });
        untypedParameters.put("timezoneOffset", new String[] {String.valueOf(- time.getOffset().getTotalSeconds() / 60)});
        untypedParameters.put("interval", new String[] { interval.toString() });
        return untypedParameters;
    }

    private <T, U> void assertDoesNotContain(T value, Collection<U> collection, Function<U, T> transformer) {
        assertNotNull("value should not be null", value);
        assertNotNull("collection should not be null", collection);
        List<T> collect = collection.stream().map(transformer).collect(Collectors.toList());
        if (collect.contains(value)) {
            fail("Expected to NOT find <" + value + "> in " + collect);
        }
    }

    private <T, U> void assertContains(T value, Collection<U> collection, Function<U, T> transformer) {
        assertNotNull("value should not be null", value);
        assertNotNull("collection should not be null", collection);
        List<T> collect = collection.stream().map(transformer).collect(Collectors.toList());
        if (!collect.contains(value)) {
            fail("Expected to find <" + value + "> in " + collect);
        }
    }
}
