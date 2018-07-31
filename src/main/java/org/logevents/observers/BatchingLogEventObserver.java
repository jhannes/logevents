package org.logevents.observers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.LogEventGroup;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ConfigUtil;

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

    private final LogEventBatchProcessor batchProcessor;
    private final ScheduledExecutorService executor;

    private Duration cooldownTime = Duration.ofSeconds(15);
    private Duration maximumWaitTime = Duration.ofMinutes(1);
    private Duration idleThreshold = Duration.ofSeconds(5);

    private Instant lastSendTime = Instant.ofEpochMilli(0);

    private List<LogEventGroup> currentBatch = new ArrayList<>();
    private LogEventGroup currentMessage;
    private ScheduledFuture<?> scheduledTask;

    public BatchingLogEventObserver(Properties configuration, String prefix) {
        idleThreshold = Duration.parse(configuration.getProperty(prefix + ".idleThreshold"));
        cooldownTime = Duration.parse(configuration.getProperty(prefix + ".cooldownTime"));
        maximumWaitTime = Duration.parse(configuration.getProperty(prefix + ".maximumWaitTime"));

        this.batchProcessor = ConfigUtil.create(prefix + ".batchProcessor", "org.logevents.observers.batch", configuration);

        executor = scheduledExecutorService;
        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

    public BatchingLogEventObserver(LogEventBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
        executor = scheduledExecutorService;
    }

    @Override
    public synchronized void logEvent(LogEvent logEvent) {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        Duration sendDelay = addToBatch(logEvent);
        scheduledTask = executor.schedule(this::execute, sendDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    Duration addToBatch(LogEvent logEvent) {
        if (currentMessage != null && currentMessage.isMatching(logEvent)) {
            currentMessage.add(logEvent);
        } else {
            currentMessage = new LogEventGroup(logEvent);
            currentBatch.add(currentMessage);
        }
        return nextSendDelay();
    }

    private void execute() {
        List<LogEventGroup> batch = takeCurrentBatch();
        if (!batch.isEmpty()) {
            batchProcessor.processBatch(batch);
        }
    }

    public synchronized List<LogEventGroup> takeCurrentBatch() {
        List<LogEventGroup> batch = new ArrayList<>();
        batch.addAll(currentBatch);
        currentBatch.clear();
        currentMessage = null;
        lastSendTime = Instant.now();
        return batch;
    }

    private Duration nextSendDelay() {
        if (firstEventInBatchTime().plus(maximumWaitTime).isBefore(Instant.now())) {
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
