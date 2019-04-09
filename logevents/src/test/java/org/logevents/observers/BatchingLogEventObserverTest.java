package org.logevents.observers;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.util.Configuration;
import org.mockito.Mockito;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

public class BatchingLogEventObserverTest {


    public static class Processor implements LogEventBatchProcessor {
        List<LogEventBatch> batches = new ArrayList<>();

        @Override
        public void processBatch(LogEventBatch batch) {
            batches.add(batch);
        }
    }

    @Test
    public void shouldAccumulateSimilarMessages() {
        BatchingLogEventObserver observer = new BatchingLogEventObserver(null);

        String messageFormat = "This is a message about {}";
        LogEventSampler sampler = new LogEventSampler()
                .withLoggerName(getClass().getName())
                .withLevel(Level.INFO)
                .withFormat(messageFormat);
        observer.addToBatch(sampler.withArgs("cheese").build(), Instant.now());
        observer.addToBatch(sampler.withArgs("ham").build(), Instant.now());

        LogEventBatch batch = observer.takeCurrentBatch();
        assertEquals(1, batch.groups().size());
        assertEquals(messageFormat, batch.firstHighestLevelLogEventGroup().getMessage());
        assertArrayEquals(new Object[] { "cheese" }, batch.firstHighestLevelLogEventGroup().headMessage().getArgumentArray());
        assertEquals(2, batch.firstHighestLevelLogEventGroup().size());

        assertEquals(0, observer.takeCurrentBatch().size());
    }

    @Test
    public void shouldCalculateNextSendDelay() throws InterruptedException {
        Properties configuration = new Properties();
        Duration idleThreshold = Duration.ofSeconds(10);
        configuration.setProperty("observers.batch.idleThreshold", idleThreshold.toString());
        configuration.setProperty("observers.batch.cooldownTime", Duration.ofSeconds(15).toString());
        configuration.setProperty("observers.batch.maximumWaitTime", Duration.ofMinutes(2).toString());
        configuration.setProperty("observers.batch.batchProcessor", Processor.class.getName());
        BatchingLogEventObserver observer = new BatchingLogEventObserver(configuration, "observers.batch");

        LogEvent message = new LogEventSampler().build();
        Thread.sleep(20);
        Instant sendTime = observer.addToBatch(message, Instant.now());
        assertEquals(message.getInstant().plus(idleThreshold), sendTime);
    }

    @Test
    public void shouldProcessMessages() throws InterruptedException {
        LogEventBatchProcessor processor = Mockito.mock(LogEventBatchProcessor.class);
        BatchingLogEventObserver observer = new BatchingLogEventObserver(processor);
        Duration maximumWaitTime = Duration.ofMinutes(10);
        observer.setMaximumWaitTime(maximumWaitTime);

        Instant now = ZonedDateTime.of(2018, 8, 1, 20, 0, 0, 0, ZoneId.systemDefault()).toInstant();

        LogEvent firstMessage = new LogEventSampler()
                .withTime(now.minus(maximumWaitTime).minus(Duration.ofMillis(100)))
                .build();
        Instant firstMessageSend = observer.logEvent(firstMessage, firstMessage.getInstant());
        assertTrue(firstMessageSend + " should be after " + firstMessage.getInstant(),
                firstMessageSend.isAfter(firstMessage.getInstant()));

        LogEvent secondMessage = new LogEventSampler().withTime(now).build();
        Instant secondMessageSend = observer.logEvent(secondMessage, secondMessage.getInstant());
        assertTrue(secondMessageSend + " should not be after " + secondMessage.getInstant(), !secondMessageSend.isAfter(secondMessage.getInstant()));

        observer.awaitTermination(50, TimeUnit.MILLISECONDS);
        verify(processor).processBatch(new LogEventBatch().add(firstMessage).add(secondMessage));
    }

    @Test
    public void shouldSendSeparateBatchesForThrottledMarkers() throws InterruptedException {
        Marker myMarker = MarkerFactory.getMarker("MY_MARKER");

        LogEventBatchProcessor processor = Mockito.mock(LogEventBatchProcessor.class);
        BatchingLogEventObserver observer = new BatchingLogEventObserver(processor);

        Properties properties = new Properties();
        properties.put("observer.test.markers.MY_MARKER.throttle", "PT0.1S PT0.3S");
        observer.configureMarkers(new Configuration(properties, "observer.test"));
        observer.getMarker(myMarker).setBatchProcessor(processor);

        LogEvent event = new LogEventSampler().withMarker(myMarker).build();
        observer.logEvent(event);
        observer.awaitTermination(100, TimeUnit.MILLISECONDS);
        verify(processor).processBatch(new LogEventBatch().add(event));
    }


}
