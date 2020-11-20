package org.logevents.observers.batch;

import org.logevents.config.Configuration;
import org.logevents.observers.AbstractBatchingLogEventObserver;
import org.logevents.status.LogEventStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Used to gather up a number of log event to process as a batch.
 * <p>
 * The batch decides when to flush a batch based on
 * {@link #setCooldownTime(Duration)} (minimum time since last processed batch),
 * {@link #setIdleThreshold(Duration)} (minimum time between log events in the batch)
 * and {@link #setMaximumWaitTime(Duration)} (maximum time from the first message in a batch
 * was generated until the batch is processed). When processing, the
 * {@link AbstractBatchingLogEventObserver} calls {@link #processor}
 * with the whole batch to process the log events. Consecutive Log events with
 * the same message pattern and log level are grouped together so the processor
 * can easily ignore duplicate messages.
 *
 * @author Johannes Brodwall
 */
public class CooldownBatcher<T> implements Batcher<T> {
    private Instant lastFlushTime = Instant.EPOCH;
    private List<T> batch = new ArrayList<>();

    protected Duration cooldownTime = Duration.ofSeconds(15);
    protected Duration maximumWaitTime = Duration.ofMinutes(1);
    protected Duration idleThreshold = Duration.ofSeconds(5);

    private final ScheduledExecutorService executor;
    private Instant batchStartedTime;
    private final Consumer<List<T>> processor;

    private ScheduledFuture<?> task;

    public CooldownBatcher(Consumer<List<T>> processor, ScheduledExecutorService executor) {
        this.processor = processor;
        this.executor = executor;
    }

    /**
     * Read <code>idleThreshold</code>, <code>cooldownTime</code> and <code>maximumWaitTime</code>
     * from configuration
     */
    public void configure(Configuration configuration, String prefix) {
        idleThreshold = configuration.optionalDuration(prefix + "idleThreshold").orElse(idleThreshold);
        cooldownTime = configuration.optionalDuration(prefix + "cooldownTime").orElse(cooldownTime);
        maximumWaitTime = configuration.optionalDuration(prefix + "maximumWaitTime").orElse(maximumWaitTime);
    }

    /**
     * Minimum time from a flush until the next flush
     */
    public void setCooldownTime(Duration cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    /**
     * Minimum time from a flush until the next flush
     */
    public void setIdleThreshold(Duration idleThreshold) {
        this.idleThreshold = idleThreshold;
    }

    /**
     * Maximum time from the first event in a batch until the batch is flushed
     */
    public void setMaximumWaitTime(Duration maximumWaitTime) {
        this.maximumWaitTime = maximumWaitTime;
    }

    public void accept(T o) {
        add(o, Instant.now());
    }

    protected synchronized void add(T o, Instant now) {
        batch.add(o);
        updateSchedule(now);
    }

    private void updateSchedule(Instant now) {
        if (batchStartedTime == null) {
            this.batchStartedTime = Instant.now();
        }
        Instant latestFlushTime = batchStartedTime.plus(maximumWaitTime);
        Instant sendTime = earliest(now);

        if (sendTime.isAfter(latestFlushTime)) {
            sendTime = latestFlushTime;
        }
        Duration delay = Duration.between(now, sendTime);
        scheduleFlush(delay);
    }

    /**
     * Returns lastFlushTime + cooldownTime or lastEventTime + idleThreshold, whichever is latest
     */
    private Instant earliest(Instant now) {
        Instant idleTimeout = now.plus(idleThreshold);
        Instant cooldownTimeout = lastFlushTime.plus(cooldownTime);
        return cooldownTimeout.isAfter(idleTimeout) ? cooldownTimeout : idleTimeout;
    }


    protected synchronized void scheduleFlush(Duration delay) {
        if (task != null) {
            LogEventStatus.getInstance().addTrace(this, "Cancelling existing timer");
            task.cancel(false);
        }
        LogEventStatus.getInstance().addTrace(this, "Scheduling flush in " + delay);
        task = executor.schedule(this::flush, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void flush() {
        LogEventStatus.getInstance().addDebug(this, "Flushing");
        List<T> currentBatch = takeCurrentBatch();
        task = null;

        try {
            processor.accept(currentBatch);
        } catch (Exception e) {
            LogEventStatus.getInstance().addFatal(this, "Failed to process batch", e);
        }
    }

    public void shutdown() {
        flush();
    }

    protected synchronized List<T> takeCurrentBatch() {
        List<T> currentBatch = this.batch;
        batch = new ArrayList<>();
        batchStartedTime = null;
        lastFlushTime = Instant.now();
        return currentBatch;
    }

    @Override
    public List<T> getCurrentBatch() {
        return batch;
    }

    void setLastFlushTime(Instant lastFlushTime) {
        this.lastFlushTime = lastFlushTime;
    }

}
