package org.logevents.observers.batch;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;

public class BatchingLogEventObserver implements LogEventObserver {

    private final LogEventBatchProcessor batchProcessor;
    private final ScheduledExecutorService executor;

    private Duration cooldownTime;
    private Duration maximumWaitTime;
    private Duration idleThreshold;

    private Instant lastSendTime = Instant.ofEpochMilli(0);

    private List<LogEventGroup> currentBatch = new ArrayList<>();
    private LogEventGroup currentMessage;
    private ScheduledFuture<?> scheduledTask;

    public BatchingLogEventObserver(LogEventBatchProcessor batchProcessor, ScheduledExecutorService executor) {
        this.batchProcessor = batchProcessor;
        this.executor = executor;
    }

    @Override
    public synchronized void logEvent(LogEvent logEvent) {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        addToBatch(logEvent);
        Duration sendDelay = nextSendDelay();
        scheduledTask = executor.schedule(this::execute, sendDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void addToBatch(LogEvent logEvent) {
        if (currentMessage != null && currentMessage.isMatching(logEvent)) {
            currentMessage.add(logEvent);
        } else {
            currentMessage = new LogEventGroup(logEvent);
            currentBatch.add(currentMessage);
        }
    }

    private void execute() {
        List<LogEventGroup> batch = takeCurrentBatch();
        if (!batch.isEmpty()) {
            batchProcessor.processBatch(batch);
        }
    }

    private synchronized List<LogEventGroup> takeCurrentBatch() {
        List<LogEventGroup> batch = new ArrayList<>();
        batch.addAll(currentBatch);
        currentBatch.clear();
        currentMessage = null;
        lastSendTime = Instant.now();
        return batch;
    }

    private Duration nextSendDelay() {
        if (currentBatch.isEmpty()) {
            return Duration.ofHours(1);
        } else if (firstEventInBatchTime().plus(maximumWaitTime).isBefore(Instant.now())) {
            // We have waited long enough - send it now!
            return Duration.ZERO;
        } else {
            // Wait the necessary time before sending - may be in the past
            return Duration.between(Instant.now(), earliestSendTime());
        }
    }

    private Instant earliestSendTime() {
        Instant idleTimeout = latestEventInBatchTime().plus(idleThreshold);
        Instant cooldownTimeout = lastSendTime.plus(cooldownTime);
        return idleTimeout.isAfter(cooldownTimeout) ? idleTimeout : cooldownTimeout;
    }

    private Instant firstEventInBatchTime() {
        return currentBatch.get(0).firstEventTime();
    }

    private Instant latestEventInBatchTime() {
        return currentMessage.latestEventTime();
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
