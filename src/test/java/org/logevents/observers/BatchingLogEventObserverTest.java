package org.logevents.observers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.LogEventGroup;
import org.slf4j.event.Level;

public class BatchingLogEventObserverTest {


    public static class Processor implements LogEventBatchProcessor {
        List<List<LogEventGroup>> batches = new ArrayList<>();

        @Override
        public void processBatch(List<LogEventGroup> batch) {
            batches.add(batch);
        }
    }

    @Test
    public void shouldAccumulateSimilarMessages() {
        BatchingLogEventObserver observer = new BatchingLogEventObserver(null);

        String messageFormat = "This is a message about {}";
        observer.addToBatch(sampleMessage(messageFormat, Level.INFO, "cheese"), Instant.now());
        observer.addToBatch(sampleMessage(messageFormat, Level.INFO, "ham"), Instant.now());

        List<LogEventGroup> batch = observer.takeCurrentBatch();
        assertEquals(1, batch.size());
        assertEquals(messageFormat, batch.get(0).headMessage().getMessage());
        assertArrayEquals(new Object[] { "cheese" }, batch.get(0).headMessage().getArgumentArray());
        assertEquals(2, batch.get(0).size());

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

        LogEvent message = sampleMessage("This is a strange message", Level.INFO);
        Thread.sleep(20);
        Instant sendTime = observer.addToBatch(message, Instant.now());
        assertEquals(message.getInstant().plus(idleThreshold), sendTime);
    }

    @Test
    public void shouldProcessMessages() throws InterruptedException, TimeoutException {
        Processor processor = new Processor();
        BatchingLogEventObserver observer = new BatchingLogEventObserver(processor);
        Duration maximumWaitTime = Duration.ofMinutes(10);
        observer.setMaximumWaitTime(maximumWaitTime);

        Instant now = ZonedDateTime.of(2018, 8, 1, 20, 0, 0, 0, ZoneId.systemDefault()).toInstant();

        LogEvent firstMessage = new LogEvent(getClass().getName(), Level.INFO, now.minus(maximumWaitTime).minus(Duration.ofMillis(100)), null, "a",
                new Object[0]);
        Instant firstMessageSend = observer.logEvent(firstMessage, firstMessage.getInstant());
        assertTrue(firstMessageSend + " should be after " + firstMessage.getInstant(),
                firstMessageSend.isAfter(firstMessage.getInstant()));

        LogEvent secondMessage = new LogEvent(getClass().getName(), Level.INFO, now, null, "b",
                new Object[0]);
        Instant secondMessageSend = observer.logEvent(secondMessage, secondMessage.getInstant());
        assertTrue(secondMessageSend + " should not be after " + secondMessage.getInstant(), !secondMessageSend.isAfter(secondMessage.getInstant()));

        observer.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertEquals(1, processor.batches.size());
        assertEquals(2, processor.batches.get(0).size());
    }

    private LogEvent sampleMessage(String messageFormat, Level level, Object... objects) {
        return new LogEvent(getClass().getName(), level, messageFormat, objects);
    }

}
