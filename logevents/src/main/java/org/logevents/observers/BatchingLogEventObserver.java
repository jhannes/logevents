package org.logevents.observers;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

/**
 * Used to gather up a number of log event to process as a batch. This is useful
 * when using logging destinations where high frequency of messages would be
 * inefficient or noisy, such as email or Slack.
 * <p>
 * The batch observers decides when to process a batch based on
 * {@link #cooldownTime} (minimum time since last processed batch),
 * {@link #idleThreshold} (minimum time between log events in the batch)
 * and {@link #maximumWaitTime} (maximum time from the first message in a batch
 * was generated until the batch is processed). When processing, the
 * {@link BatchingLogEventObserver} sends the whole batch to a
 * {@link LogEventBatchProcessor} for processing. Consecutive Log events with
 * the same message pattern and log level are grouped together so the processor
 * can easily ignore duplicate messages.
 *
 * @author Johannes Brodwall
 */
public class BatchingLogEventObserver implements LogEventObserver {

    private static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3,
            new ThreadFactory() {
                private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = defaultFactory.newThread(r);
                    thread.setName("LogEvent$ScheduleExecutor-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    protected final LogEventBatchProcessor batchProcessor;
    private final ScheduledExecutorService executor;

    protected Level threshold = Level.TRACE;

    protected Duration cooldownTime = Duration.ofSeconds(15);
    protected Duration maximumWaitTime = Duration.ofMinutes(1);
    protected Duration idleThreshold = Duration.ofSeconds(5);

    private Instant lastSendTime = Instant.ofEpochMilli(0);

    private LogEventBatch currentBatch = new LogEventBatch();
    private ScheduledFuture<?> scheduledTask;

    public BatchingLogEventObserver(Properties properties, String prefix) {
        Configuration configuration = new Configuration(properties, prefix);

        threshold = configuration.optionalString("threshold").map(Level::valueOf).orElse(Level.DEBUG);
        idleThreshold = configuration.optionalDuration("idleThreshold").orElse(idleThreshold);
        cooldownTime = configuration.optionalDuration("cooldownTime").orElse(cooldownTime);
        maximumWaitTime = configuration.optionalDuration("maximumWaitTime").orElse(maximumWaitTime);
        batchProcessor = configuration.createInstance("batchProcessor", LogEventBatchProcessor.class);
        configuration.checkForUnknownFields();

        executor = scheduledExecutorService;
        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

    public BatchingLogEventObserver(LogEventBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
        executor = scheduledExecutorService;
    }

    /**
     * Block until the current batch is processed by the internal scheduler.
     * Especially useful for testing.
     */
    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        scheduledExecutorService.awaitTermination(timeout, unit);
    }

    @Override
    public synchronized void logEvent(LogEvent logEvent) {
        logEvent(logEvent, Instant.now());
    }

    Instant logEvent(LogEvent logEvent, Instant now) {
        if (threshold.toInt() <= logEvent.getLevel().toInt()) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            Instant sendTime = addToBatch(logEvent, now);
            Duration duration = Duration.between(now, sendTime);
            scheduledTask = executor.schedule(this::execute, duration.toMillis(), TimeUnit.MILLISECONDS);
            return sendTime;
        } else {
            return null;
        }
    }

    Instant addToBatch(LogEvent logEvent, Instant now) {
        currentBatch.add(logEvent);
        return nextSendDelay(now);
    }

    private void execute() {
        LogEventBatch batch = takeCurrentBatch();
        if (!batch.isEmpty()) {
            try {
                batchProcessor.processBatch(batch);
            } catch (Exception e) {
                LogEventStatus.getInstance().addFatal(this, "Failed to process batch", e);
            }
        }
    }

    public synchronized LogEventBatch takeCurrentBatch() {
        lastSendTime = Instant.now();
        LogEventBatch returnedBatch = this.currentBatch;
        currentBatch = new LogEventBatch();

        return returnedBatch;
    }

    private Instant nextSendDelay(Instant now) {
        if (firstEventInBatchTime().plus(maximumWaitTime).isBefore(now)) {
            // We have waited long enough - send it now!
            return now;
        } else {
            // Wait the necessary time before sending - may be in the past
            return earliestSendTime();
        }
    }

    private Instant earliestSendTime() {
        Instant idleTimeout = latestEventInBatchTime().plus(idleThreshold);
        Instant cooldownTimeout = lastSendTime.plus(cooldownTime);
        return idleTimeout.isAfter(cooldownTimeout) ? idleTimeout : cooldownTimeout;
    }

    private Instant firstEventInBatchTime() {
        return currentBatch.firstEventTime();
    }

    private Instant latestEventInBatchTime() {
        return currentBatch.latestEventTime();
    }

    public void setCooldownTime(Duration cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public void setMaximumWaitTime(Duration maximumWaitTime) {
        this.maximumWaitTime = maximumWaitTime;
    }

    public void setIdleThreshold(Duration idleThreshold) {
        this.idleThreshold = idleThreshold;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{batchProcessor=" + this.batchProcessor + "}";
    }
}
