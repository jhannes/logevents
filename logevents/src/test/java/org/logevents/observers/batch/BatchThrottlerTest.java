package org.logevents.observers.batch;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BatchThrottlerTest {

    private List<LogEventBatch> batches = new ArrayList<>();
    private Scheduler mockExecutor = mock(Scheduler.class);
    private BatchThrottler batcher = new BatchThrottler(mockExecutor, batches::add)
            .setThrottle(Arrays.asList(Duration.ofMinutes(1), Duration.ofMinutes(5)));

    @Before
    public void shouldSetAction() {
        verify(mockExecutor).setAction(any());
    }

    @Test
    public void shouldScheduleFirstEventForImmediateExecution() {
        batcher.logEvent(new LogEventSampler().build());
        verify(mockExecutor).scheduleFlush(Duration.ZERO);
    }

    @Test
    public void shouldSendOnFlush() {
        LogEvent logEvent = new LogEventSampler().build();
        batcher.logEvent(logEvent);
        batcher.execute();
        assertEquals(Arrays.asList(new LogEventBatch().add(logEvent)), batches);
    }

    @Test
    public void shouldDelaySubsequentEvents() {
        batcher.logEvent(new LogEventSampler().build());
        verify(mockExecutor).scheduleFlush(Duration.ZERO);
        batcher.execute();

        batcher.logEvent(new LogEventSampler().build());
        verify(mockExecutor).scheduleFlush(Duration.ofMinutes(1));
        batcher.logEvent(new LogEventSampler().build());
        verifyNoMoreInteractions(mockExecutor);
    }

    @Test
    public void shouldIncreaseDelay() {
        batcher.logEvent(new LogEventSampler().build());
        verify(mockExecutor).scheduleFlush(Duration.ZERO);

        batcher.execute();
        verify(mockExecutor).scheduleFlush(Duration.ofMinutes(1));
        batcher.logEvent(new LogEventSampler().build());
        batcher.execute();
        verify(mockExecutor).scheduleFlush(Duration.ofMinutes(5));

        batcher.logEvent(new LogEventSampler().build());
        batcher.execute();
        verify(mockExecutor, times(2)).scheduleFlush(Duration.ofMinutes(5));

        batcher.execute();
        verifyNoMoreInteractions(mockExecutor);
    }

}