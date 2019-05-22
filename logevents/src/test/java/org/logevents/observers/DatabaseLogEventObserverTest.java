package org.logevents.observers;

import junit.framework.AssertionFailedError;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.query.LogEventFilter;
import org.logevents.query.LogEventSummary;
import org.logevents.util.Configuration;
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
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DatabaseLogEventObserverTest {

    private final Marker MY_MARKER = MarkerFactory.getMarker("MY_MARKER");
    private final Marker OTHER_MARKER = MarkerFactory.getMarker("OTHER_MARKER");

    private Properties properties = new Properties();
    {
        properties.setProperty("observer.db.jdbcUrl", "jdbc:h2:mem:logevents-test;DB_CLOSE_DELAY=-1");
        properties.setProperty("observer.db.jdbcUsername", "sa");
        properties.setProperty("observer.db.jdbcPassword", "");
        properties.setProperty("observer.db.logEventsTable", "test_log_events");
        properties.setProperty("observer.db.logEventsTable", "test_log_mdc");
    }
    private DatabaseLogEventObserver observer = new DatabaseLogEventObserver(properties, "observer.db");

    @Test
    public void shouldListSavedLogEvents() {
        LogEvent event = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(event));

        Collection<LogEvent> events = observer.filter(filter(Optional.ofNullable(event.getLevel()), event.getZonedDateTime(), Duration.ofSeconds(1)));
        assertContains(event.getMessage(), events, LogEvent::getMessage);
    }

    @Test
    public void shouldSaveAllFields() {
        LogEvent event = new LogEventSampler().withFormat(UUID.randomUUID().toString())
                .withArgs(LogEventSampler.randomString(), LogEventSampler.randomString())
                .build();
        observer.processBatch(new LogEventBatch().add(event));

        LogEvent savedEvent = observer
                .filter(filter(Optional.of(event.getLevel()), event.getZonedDateTime(), Duration.ofSeconds(1)))
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
    public void shouldFilterOnThreshold() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters;
        parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        assertContains(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);

        parameters.put("level", new String[] { Level.WARN.toString() });
        assertDoesNotContain(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);
    }

    @Test
    public void shouldFilterOnMarkers() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("marker", new String[] { "NON_EXISTING_MARKER" });
        assertDoesNotContain(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);
        parameters.put("marker", new String[] { "NON_EXISTING_MARKER", event.getMarker().getName() });
        assertContains(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);
    }

    @Test
    public void shouldFilterOnLoggers() {
        LogEvent event = new LogEventSampler().withLevel(Level.INFO).withMarker().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("logger", new String[] { "org.example.non.Existing" });
        assertDoesNotContain(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);
        parameters.put("logger", new String[] { "org.example.non.Existing", event.getLoggerName() });
        assertContains(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);
    }

    @Test
    public void shouldFilterOnThreads() {
        LogEvent event = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(event));

        HashMap<String, String[]> parameters = parameters(Optional.of(Level.INFO), event.getZonedDateTime(), Duration.ofSeconds(10));
        parameters.put("thread", new String[] { "Thread-nonexisting" });
        assertDoesNotContain(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);
        parameters.put("thread", new String[] { "Thread-nonexisting", event.getThreadName() });
        assertContains(event.getMessage(), observer.filter(new LogEventFilter(parameters)), LogEvent::getMessage);
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
        Collection<LogEvent> events = observer.filter(new LogEventFilter(parameters));
        assertDoesNotContain(event1.getMessage(), events, LogEvent::getMessage);
        assertDoesNotContain(event2.getMessage(), events, LogEvent::getMessage);
        assertContains(event3.getMessage(), events, LogEvent::getMessage);

        parameters.put("mdc[op]", new String[] { "secondOp" });
        events = observer.filter(new LogEventFilter(parameters));
        assertDoesNotContain(event1.getMessage(), events, LogEvent::getMessage);
        assertContains(event2.getMessage(), events, LogEvent::getMessage);
        assertContains(event3.getMessage(), events, LogEvent::getMessage);

        parameters.put("mdc[op]", new String[] { "firstOp", "secondOp" });
        events = observer.filter(new LogEventFilter(parameters));
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

        LogEvent savedEvent = observer
                .filter(filter(Optional.ofNullable(event.getLevel()), event.getZonedDateTime(), Duration.ofSeconds(1)))
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

        Collection<LogEvent> events = observer.filter(filter(Optional.of(Level.TRACE), ZonedDateTime.now(), Duration.ofHours(1)));
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

        Collection<LogEvent> events = observer.filter(filter(Optional.of(Level.INFO), ZonedDateTime.now(), Duration.ofHours(1)));
        assertContains(higherEvent.getMessage(), events, LogEvent::getMessage);
        assertContains(matchingEvent.getMessage(), events, LogEvent::getMessage);
        assertDoesNotContain(lowerEvent.getMessage(), events, LogEvent::getMessage);
    }

    @Test
    public void shouldIncludeMdcInFacets() throws SQLException {
        observer.processBatch(new LogEventBatch()
                .add(new LogEventSampler().withMdc("ip", "127.0.0.1").withMdc("url", "/api/op").build())
                .add(new LogEventSampler().withMdc("ip", "127.0.0.1").build())
                .add(new LogEventSampler().withMdc("ip", "10.0.0.4").build()));

        LogEventSummary summary = observer.getSummary(filter(Optional.empty(), ZonedDateTime.now(), Duration.ofMinutes(10)));
        assertEquals(new HashSet<>(Arrays.asList("ip", "url")), summary.getMdcMap().keySet());
        assertEquals(new HashSet<>(Arrays.asList("127.0.0.1", "10.0.0.4")), summary.getMdcMap().get("ip"));
        assertEquals(new HashSet<>(Arrays.asList("/api/op")), summary.getMdcMap().get("url"));
    }

    private LogEventFilter filter(Optional<Level> level, ZonedDateTime time, Duration interval) {
        HashMap<String, String[]> untypedParameters = parameters(level, time, interval);
        return new LogEventFilter(untypedParameters);
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

        LogEventSummary summary = observer.getSummary(filter(Optional.empty(), logTime, Duration.ofHours(1)));
        assertEquals(new HashSet<>(Arrays.asList(event1.getMarker().getName(), event3.getMarker().getName())),
                summary.getMarkers());
        assertEquals(new HashSet<>(Arrays.asList(event1.getLoggerName(), event2.getLoggerName(), event3.getLoggerName())),
                summary.getLoggers());
        assertEquals(new HashSet<>(Arrays.asList(event1.getThreadName(), event2.getThreadName(), event3.getThreadName())),
                summary.getThreads());
        assertEquals(new HashSet<>(Arrays.asList(Configuration.calculateNodeName())), summary.getNodes());
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

        LogEventSummary summary = observer.getSummary(filter(Optional.of(Level.WARN), time, interval));
        assertEquals(new HashSet<>(Arrays.asList("mdcKey")), summary.getMdcMap().keySet());
        assertEquals(new HashSet<>(Arrays.asList("just right")), summary.getMdcMap().get("mdcKey"));
        Map<String, Object> facets = summary.toJson();
        assertEquals(new HashSet<>(Arrays.asList(OTHER_MARKER.toString())), summary.getMarkers());
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