package org.logevents.observers.batch;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.junit.LogEventSampler;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class CooldownBatcherTest {
    private ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

    @Test
    public void shouldAccumulateSimilarMessages() {

        CooldownBatcher<LogEvent> observer = new CooldownBatcher<>(null, executor);

        String messageFormat = "This is a message about {}";
        LogEventSampler sampler = new LogEventSampler()
                .withLoggerName(getClass().getName())
                .withLevel(Level.INFO)
                .withFormat(messageFormat);
        observer.add(sampler.withArgs("cheese").build(), Instant.now());
        observer.add(sampler.withArgs("ham").build(), Instant.now());

        LogEventBatch batch =  new LogEventBatch(observer.takeCurrentBatch());
        assertEquals(1, batch.groups().size());
        assertEquals(messageFormat, batch.firstHighestLevelLogEventGroup().getMessage());
        assertArrayEquals(new Object[] { "cheese" }, batch.firstHighestLevelLogEventGroup().headMessage().getArgumentArray());
        assertEquals(2, batch.firstHighestLevelLogEventGroup().size());

        assertEquals(0, observer.takeCurrentBatch().size());
    }

    @Test
    public void shouldProcessMessages() {
        List<List<LogEvent>> batches = new ArrayList<>();
        ScheduledExecutorService mock = mock(ScheduledExecutorService.class);
        CooldownBatcher<LogEvent> observer = new CooldownBatcher<>(batches::add, mock);

        Duration maximumWaitTime = Duration.ofMinutes(10);
        observer.setMaximumWaitTime(maximumWaitTime);

        Instant now = ZonedDateTime.of(2018, 8, 1, 20, 0, 0, 0, ZoneId.systemDefault()).toInstant();

        LogEvent firstMessage = new LogEventSampler()
                .withTime(now.minus(maximumWaitTime).minus(Duration.ofMillis(100)))
                .build();
        observer.add(firstMessage, Instant.now());

        LogEvent secondMessage = new LogEventSampler().withTime(now).build();
        observer.add(secondMessage, Instant.now());

        ArgumentCaptor<Runnable> executeArg = ArgumentCaptor.forClass(Runnable.class);
        verify(mock, times(2)).schedule(executeArg.capture(), anyLong(), any());

        executeArg.getValue().run();
        assertEquals(Arrays.asList(Arrays.asList(firstMessage, secondMessage)),
                batches);
    }


    private static class TestCooldownBatcher extends CooldownBatcher<Object> {

        Duration nextDelay;

        public TestCooldownBatcher(Consumer callback, ScheduledExecutorService executor) {
            super(callback, executor);
        }

        @Override
        protected synchronized void scheduleFlush(Duration delay) {
            this.nextDelay = delay;
        }
    }

    private TestCooldownBatcher cooldownBatcher = new TestCooldownBatcher(null, null);

    @Test
    public void shouldWaitIdleThresholdBeforeFlushing() {
        cooldownBatcher.setIdleThreshold(Duration.ofSeconds(2));
        cooldownBatcher.add(new Object(), Instant.now());
        assertEquals(Duration.ofSeconds(2), cooldownBatcher.nextDelay);
    }

    @Test
    public void shouldWaitMinimumCooldownThreshold() {
        cooldownBatcher.setCooldownTime(Duration.ofSeconds(30));
        Instant start = Instant.now();
        cooldownBatcher.setLastFlushTime(start);
        cooldownBatcher.add(new Object(), start.plusSeconds(10));
        assertEquals(Duration.ofSeconds(20), cooldownBatcher.nextDelay);
    }

    @Test
    public void shouldNeverWaitLongerThanMaxWait() {
        cooldownBatcher.setIdleThreshold(Duration.ofSeconds(25));
        cooldownBatcher.setMaximumWaitTime(Duration.ofMinutes(1));
        Instant start = Instant.now();
        cooldownBatcher.add(new Object(), start);
        cooldownBatcher.add(new Object(), start.plusSeconds(24));
        cooldownBatcher.add(new Object(), start.plusSeconds(48));

        assertEquals(Duration.ofSeconds(12), cooldownBatcher.nextDelay.withNanos(0));
    }
}
