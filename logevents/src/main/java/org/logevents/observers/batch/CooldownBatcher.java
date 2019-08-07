package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.observers.BatchingLogEventObserver;
import org.logevents.status.LogEventStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Used to gather up a number of log event to process as a batch.
 * <p>
 * The batch decides when to flush a batch based on
 * {@link #setCooldownTime(Duration)} (minimum time since last processed batch),
 * {@link #setIdleThreshold(Duration)} (minimum time between log events in the batch)
 * and {@link #setMaximumWaitTime(Duration)} (maximum time from the first message in a batch
 * was generated until the batch is processed). When processing, the
 * {@link BatchingLogEventObserver} calls {@link #processor}
 * with the whole batch to process the log events. Consecutive Log events with
 * the same message pattern and log level are grouped together so the processor
 * can easily ignore duplicate messages.
 *
 * @author Johannes Brodwall
 */
public class CooldownBatcher implements LogEventObserver {
    private Instant lastSendTime = Instant.ofEpochMilli(0);
    private LogEventBatch currentBatch = new LogEventBatch();

    protected Duration cooldownTime = Duration.ofSeconds(15);
    protected Duration maximumWaitTime = Duration.ofMinutes(1);
    protected Duration idleThreshold = Duration.ofSeconds(5);

    private Scheduler flusher;
    private Consumer<LogEventBatch> processor;

    public CooldownBatcher(Scheduler flusher, Consumer<LogEventBatch> processor) {
        this.flusher = flusher;
        this.processor = processor;
        flusher.setAction(this::execute);
    }

    @Override
    public void logEvent(LogEvent e) {
        Instant now = Instant.now();
        Instant sendTime = addToBatch(e, now);
        Duration duration = Duration.between(now, sendTime);
        flusher.scheduleFlush(duration);
    }

    public Instant addToBatch(LogEvent logEvent, Instant now) {
        currentBatch.add(logEvent);
        return nextSendDelay(now);
    }

    public LogEventBatch takeCurrentBatch() {
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

    /**
     * Read <code>idleThreshold</code>, <code>cooldownTime</code> and <code>maximumWaitTime</code>
     * from configuration
     */
    public void configure(Configuration configuration) {
        idleThreshold = configuration.optionalDuration("idleThreshold").orElse(idleThreshold);
        cooldownTime = configuration.optionalDuration("cooldownTime").orElse(cooldownTime);
        maximumWaitTime = configuration.optionalDuration("maximumWaitTime").orElse(maximumWaitTime);
    }

    public void execute() {
        LogEventBatch batch = takeCurrentBatch();
        if (!batch.isEmpty()) {
            try {
                processor.accept(batch);
            } catch (Exception e) {
                LogEventStatus.getInstance().addFatal(this, "Failed to process batch", e);
            }
        }
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
}
