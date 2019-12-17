package org.logevents.observers.batch;

import org.logevents.status.LogEventStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Batches up all accepted object according to the throttling strategy. E.g. PT0S PT1M PT10M PT30M will process
 * the first object immediately, then batch up any objects for the next minute. If any objects were received
 * during this interval, then all object for the next ten minutes will be batched. Resets back to processing
 * immediately if there were no objects in an interval.
 * @param <T> The type of objects to batch
 */
public class ThrottlingBatcher<T> implements Batcher<T> {
    private final ScheduledExecutorService executor;
    private final Consumer<List<T>> callback;
    private int throttleIndex = 0;
    private List<Duration> throttles = new ArrayList<>();
    private ScheduledFuture<?> currentTask;
    private List<T> batch = new ArrayList<>();

    /**
     * @param throttles Durations as strings, e.g. "PT1M PT1H" for 0 seconds, 1 minute, 1 hour. Used
     *                  to determine the time to wait for successive flushes
     * @param callback The method to call when flushing. Will be called with an empty list when throttle is reset
     * @param executor The consumer used to schedule flushes
     */
    public ThrottlingBatcher(String throttles, Consumer<List<T>> callback, ScheduledExecutorService executor) {
        this.throttles.add(Duration.ofSeconds(0));
        for (String throttle : throttles.split("\\s+")) {
            this.throttles.add(Duration.parse(throttle.trim()));
        }
        this.callback = callback;
        this.executor = executor;
    }

    public synchronized void accept(T o) {
        batch.add(o);
        if (currentTask == null) {
            throttleIndex = 0;
            currentTask = executor.schedule(this::flush, throttles.get(0).toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    synchronized void flush() {
        List<T> currentBatch = this.batch;
        this.batch = Collections.synchronizedList(new ArrayList<>());
        LogEventStatus.getInstance().addTrace(this, "flush " + currentBatch.size() + " messages");
        try {
            callback.accept(currentBatch);
        } catch (Exception e) {
            LogEventStatus.getInstance().addError(this, "Error while processing batch", e);
        }

        if (currentBatch.isEmpty()) {
            throttleIndex = 0;
            currentTask = null;
            return;
        }
        if (throttleIndex < throttles.size()-1) {
            throttleIndex++;
        }
        Duration delay = throttles.get(throttleIndex);
        currentTask = executor.schedule(this::flush, delay.toMillis(), TimeUnit.MILLISECONDS);
        LogEventStatus.getInstance().addDebug(this, "Next flush in " + delay);
    }

    Duration getThrottles(int index) {
        return throttles.get(index);
    }

    @Override
    public List<T> getCurrentBatch() {
        return batch;
    }
}
