package org.logevents.observers.batch;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ThrottlingBatcherTest {

    private MockFlusher mockFlusher = new MockFlusher();
    private ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    private ThrottlingBatcher<LocalTime> batcher = new ThrottlingBatcher<>("PT1M PT15M", mockFlusher, executor);

    private static class MockFlusher implements Consumer<List<LocalTime>> {

        private List<List<LocalTime>> batches = new ArrayList<>();

        @Override
        public void accept(List<LocalTime> batch) {
            batches.add(batch);
        }
    }

    @Before
    public void setup() {
        Runnable anyRunnable = any();
        when(executor.schedule(anyRunnable, anyLong(), any())).thenReturn(mock(ScheduledFuture.class));
    }


    @Test
    public void shouldScheduleImmediately() {
        batcher.accept(LocalTime.now());
        Runnable flushCaptor = verifyFlushScheduled(Duration.ofSeconds(0));
        flushCaptor.run();
        assertEquals(1, mockFlusher.batches.size());
        assertEquals(1, mockFlusher.batches.get(0).size());
    }

    @Test
    public void shouldBatchUntilFlush() {
        batcher.accept(LocalTime.now());
        batcher.accept(LocalTime.now());
        batcher.accept(LocalTime.now());
        Runnable flushCaptor = verifyFlushScheduled(Duration.ofSeconds(0));
        flushCaptor.run();
        assertEquals(1, mockFlusher.batches.size());
        assertEquals(3, mockFlusher.batches.get(0).size());
    }

    @Test
    public void shouldIncreaseFlushDelay() {
        batcher.accept(LocalTime.now());
        Runnable flushCaptor = verifyFlushScheduled(batcher.getThrottles(0));

        flushCaptor.run();
        flushCaptor = verifyFlushScheduled(batcher.getThrottles(1));

        batcher.accept(LocalTime.now());
        flushCaptor.run();

        batcher.accept(LocalTime.now());
        flushCaptor = verifyFlushScheduled(batcher.getThrottles(2));
        flushCaptor.run();
        verifyFlushScheduled(batcher.getThrottles(2));
    }


    @Test
    public void shouldResetOnEmptyFlush() {
        batcher.accept(LocalTime.now());
        Runnable flushAction = verifyFlushScheduled(batcher.getThrottles(0));
        flushAction.run();
        flushAction = verifyFlushScheduled(batcher.getThrottles(1));

        batcher.accept(LocalTime.now());
        flushAction.run();
        flushAction = verifyFlushScheduled(batcher.getThrottles(2));
        flushAction.run();

        verifyNoMoreInteractions(executor);

        batcher.accept(LocalTime.now());
        verifyFlushScheduled(batcher.getThrottles(0));
    }

    private Runnable verifyFlushScheduled(Duration delay) {
        ArgumentCaptor<Runnable> flushCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(1))
                .schedule(flushCaptor.capture(), eq(delay.toMillis()), eq(TimeUnit.MILLISECONDS));
        reset(executor);
        Runnable anyRunnable = any();
        when(executor.schedule(anyRunnable, anyLong(), any())).thenReturn(mock(ScheduledFuture.class));
        return flushCaptor.getValue();
    }
}
