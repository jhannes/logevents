package org.logevents.observers;

import junit.framework.AssertionFailedError;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DatabaseLogEventObserverTest {

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