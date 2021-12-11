package org.logevents.observers.batch;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.optional.junit.LogEventSampler;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogEventBatcherWithMdcTest {

    private ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);

    @Test
    public void shouldCollectMessagesPerMdc() {
        BatcherFactory batcherFactory = new ThrottleBatcherFactory(executor, new LogEventShutdownHook(), "PT1M");
        Marker marker = MarkerFactory.getMarker("MARKER1");
        LogEventBatcherWithMdc batcher = new LogEventBatcherWithMdc(batcherFactory, marker.getName(), "ipAddress",
                batch ->  {});

        batcher.logEvent(new LogEventSampler().withMarker(marker).withMdc("ipAddress", "127.0.0.1").build());
        batcher.logEvent(new LogEventSampler().withMarker(marker).withMdc("ipAddress", "10.0.0.10").build());
        batcher.logEvent(new LogEventSampler().withMarker(marker).withMdc("ipAddress", "127.0.0.1").build());
        batcher.logEvent(new LogEventSampler().withMarker(marker).build());

        assertEquals(2, batcher.getBatcher("127.0.0.1").getCurrentBatch().size());
        assertEquals(1, batcher.getBatcher("10.0.0.10").getCurrentBatch().size());
        assertNull(batcher.getBatcher("0.0.0.0"));
    }

    @Test
    public void shouldScheduleFlushAfterThrottleDelayForMdc() {
        BatcherFactory batcherFactory = new ThrottleBatcherFactory(executor, new LogEventShutdownHook(), "PT1M PT10M");
        Marker marker = MarkerFactory.getMarker("MARKER1");
        LogEventBatcherWithMdc batcher = new LogEventBatcherWithMdc(batcherFactory, marker.getName(), "userId", batch -> {});
        batcher.logEvent(new LogEventSampler().withMarker(marker).withMdc("userId", "alice").build());

        batcher.getBatcher("alice");
        Runnable flusher = verifyFlushScheduled(Duration.ofSeconds(0));
        flusher.run();
        verifyFlushScheduled(Duration.ofMinutes(1));
    }

    @Test
    public void shouldRemoveMdcBatcherOnEmptyFlush() {
        BatcherFactory batcherFactory = new ThrottleBatcherFactory(executor, new LogEventShutdownHook(), "PT1M PT10M");
        Marker marker = MarkerFactory.getMarker("MARKER1");
        Consumer<List<LogEvent>> processor = Mockito.mock(Consumer.class);
        LogEventBatcherWithMdc batcher = new LogEventBatcherWithMdc(batcherFactory, marker.getName(), "userId", processor);
        batcher.logEvent(new LogEventSampler().withMarker(marker).withMdc("userId", "bob").build());

        Runnable flusher = verifyFlushScheduled(Duration.ofSeconds(0));
        flusher.run();
        Mockito.verify(processor).accept(ArgumentMatchers.any());
        flusher = verifyFlushScheduled(Duration.ofMinutes(1));
        flusher.run();
        Mockito.verify(processor).accept(new ArrayList<>());
        assertNull(batcher.getBatcher("bob"));
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
