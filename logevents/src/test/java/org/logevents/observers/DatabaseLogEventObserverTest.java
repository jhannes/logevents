package org.logevents.observers;

import junit.framework.AssertionFailedError;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.sql.SQLException;
import java.time.Instant;
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
    }
    private DatabaseLogEventObserver observer = new DatabaseLogEventObserver(properties, "observer.db");

    @Test
    public void shouldListSavedLogEvents() {
        LogEvent event = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(event));

        Collection<LogEvent> events = observer.filter(event.getLevel(), event.getInstant().minusSeconds(1), event.getInstant().plusSeconds(1));
        assertContains(event.getMessage(), events, LogEvent::getMessage);
    }

    @Test
    public void shouldSaveAllFields() {
        LogEvent event = new LogEventSampler().withFormat(UUID.randomUUID().toString())
                .withArgs(LogEventSampler.randomString(), LogEventSampler.randomString())
                .build();
        observer.processBatch(new LogEventBatch().add(event));

        LogEvent savedEvent = observer
                .filter(event.getLevel(), event.getInstant().minusSeconds(1), event.getInstant().plusSeconds(1))
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
    public void shouldSaveMdc() {
        Map<String, String> mdc = new HashMap<>();
        mdc.put("ip", "127.0.0.1");
        mdc.put("url", "/api/op");
        LogEvent event = new LogEventSampler().withFormat(UUID.randomUUID().toString())
                .withMdc(mdc)
                .build();
        observer.processBatch(new LogEventBatch().add(event));

        LogEvent savedEvent = observer
                .filter(event.getLevel(), event.getInstant().minusSeconds(1), event.getInstant().plusSeconds(1))
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

        Collection<LogEvent> events = observer.filter(Level.TRACE, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
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

        Collection<LogEvent> events = observer.filter(Level.INFO, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
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

        Map<String, Object> facets = observer.getFacets(Optional.empty(),
                ZonedDateTime.now().minusMinutes(10).toInstant(),
                ZonedDateTime.now().plusMinutes(10).toInstant());
        List<Map<String, Object>> mdc = (List<Map<String, Object>>) facets.get("mdc");

        Map<String, Object> ipMdc = mdc.stream().filter(m -> m.get("name").equals("ip")).findAny()
                .orElseThrow(() -> new IllegalArgumentException("missing ip in " + mdc));
        Map<String, Object> urlMdc = mdc.stream().filter(m -> m.get("name").equals("url")).findAny()
                .orElseThrow(() -> new IllegalArgumentException("missing ip in " + mdc));

        assertEquals(new HashSet<>(Arrays.asList("127.0.0.1", "10.0.0.4")), ipMdc.get("values"));
        assertEquals(new HashSet<>(Arrays.asList("/api/op")), urlMdc.get("values"));
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

        Map<String, Object> facets = observer.getFacets(Optional.empty(),
                logTime.minusHours(1).toInstant(),
                logTime.plusHours(1).toInstant());
        assertEquals(new HashSet<>(Arrays.asList(event1.getMarker().getName(), event3.getMarker().getName())),
                facets.get("markers"));
        assertEquals(new HashSet<>(Arrays.asList(event1.getLoggerName(), event2.getLoggerName(), event3.getLoggerName())),
                facets.get("loggers"));
        assertEquals(new HashSet<>(Arrays.asList(event1.getThreadName(), event2.getThreadName(), event3.getThreadName())),
                facets.get("threads"));
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