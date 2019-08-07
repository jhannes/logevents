package org.logevents.observers.batch;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventSampler;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CooldownBatcherTest {
    @Test
    public void shouldAccumulateSimilarMessages() {
        Scheduler mock = mock(Scheduler.class);
        CooldownBatcher observer = new CooldownBatcher(mock, null);

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
        Properties properties = new Properties();
        Duration idleThreshold = Duration.ofSeconds(10);
        properties.setProperty("observers.batch.idleThreshold", idleThreshold.toString());
        properties.setProperty("observers.batch.cooldownTime", Duration.ofSeconds(15).toString());
        properties.setProperty("observers.batch.maximumWaitTime", Duration.ofMinutes(2).toString());

        Scheduler mock = mock(Scheduler.class);
        CooldownBatcher observer = new CooldownBatcher(mock, null);
        observer.configure(new Configuration(properties, "observers.batch"));

        LogEvent message = new LogEventSampler().build();
        Thread.sleep(20);
        Instant sendTime = observer.addToBatch(message, Instant.now());
        assertEquals(message.getInstant().plus(idleThreshold), sendTime);
    }

    @Test
    public void shouldProcessMessages() {
        List<LogEventBatch> batches = new ArrayList<>();
        Scheduler mock = mock(Scheduler.class);
        CooldownBatcher observer = new CooldownBatcher(mock, batches::add);

        Duration maximumWaitTime = Duration.ofMinutes(10);
        observer.setMaximumWaitTime(maximumWaitTime);

        Instant now = ZonedDateTime.of(2018, 8, 1, 20, 0, 0, 0, ZoneId.systemDefault()).toInstant();

        LogEvent firstMessage = new LogEventSampler()
                .withTime(now.minus(maximumWaitTime).minus(Duration.ofMillis(100)))
                .build();
        observer.logEvent(firstMessage);

        LogEvent secondMessage = new LogEventSampler().withTime(now).build();
        observer.logEvent(secondMessage);

        ArgumentCaptor<Runnable> executeArg = ArgumentCaptor.forClass(Runnable.class);
        verify(mock).setAction(executeArg.capture());

        executeArg.getValue().run();
        assertEquals(Arrays.asList(new LogEventBatch().add(firstMessage).add((secondMessage))),
                batches);
    }


}