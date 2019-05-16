package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.observers.batch.BatchThrottler;
import org.logevents.observers.batch.ExecutorScheduler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.LogEventShutdownHook;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * @see SlackLogEventObserver
 * @see MicrosoftTeamsLogEventObserver
 * @see SmtpLogEventObserver
 *
 * @author Johannes Brodwall
 */
public class BatchingLogEventObserver extends FilteredLogEventObserver {

    protected static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3,
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

    static LogEventShutdownHook shutdownHook = new LogEventShutdownHook();
    static {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private final LogEventBatchProcessor batchProcessor;
    protected final ExecutorScheduler scheduler;

    protected Duration cooldownTime = Duration.ofSeconds(15);
    protected Duration maximumWaitTime = Duration.ofMinutes(1);
    protected Duration idleThreshold = Duration.ofSeconds(5);

    private Instant lastSendTime = Instant.ofEpochMilli(0);

    private LogEventBatch currentBatch = new LogEventBatch();
    private Map<Marker, BatchThrottler> markerBatchers = new HashMap<>();
    private ScheduledFuture<?> scheduledTask;

    public BatchingLogEventObserver(LogEventBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
        scheduler = new ExecutorScheduler(scheduledExecutorService, shutdownHook);
        scheduler.setAction(this::execute);
    }

    public BatchingLogEventObserver(Properties properties, String prefix) {
        Configuration configuration = new Configuration(properties, prefix);

        configureFilter(configuration);
        configureBatching(configuration);
        batchProcessor = configuration.createInstance("batchProcessor", LogEventBatchProcessor.class);
        configuration.checkForUnknownFields();

        scheduler = new ExecutorScheduler(scheduledExecutorService, shutdownHook);
        scheduler.setAction(this::execute);
    }

    /**
     * Read <code>idleThreshold</code>, <code>cooldownTime</code> and <code>maximumWaitTime</code>
     * from configuration
     */
    protected void configureBatching(Configuration configuration) {
        idleThreshold = configuration.optionalDuration("idleThreshold").orElse(idleThreshold);
        cooldownTime = configuration.optionalDuration("cooldownTime").orElse(cooldownTime);
        maximumWaitTime = configuration.optionalDuration("maximumWaitTime").orElse(maximumWaitTime);
    }

    /**
     * Block until the current batch is processed by the internal scheduler.
     * Especially useful for testing.
     */
    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        scheduler.awaitTermination(timeout, unit);
    }

    @Override
    protected final void doLogEvent(LogEvent logEvent) {
        if (logEvent.getMarker() != null) {
            for (Marker marker : markerBatchers.keySet()) {
                if (marker.contains(logEvent.getMarker())) {
                    markerBatchers.get(marker).logEvent(logEvent);
                    return;
                }
            }
        }
        logEvent(logEvent, Instant.now());
    }

    Instant logEvent(LogEvent logEvent, Instant now) {
        Instant sendTime = addToBatch(logEvent, now);
        Duration duration = Duration.between(now, sendTime);
        scheduler.schedule(duration);
        return sendTime;
    }

    Instant addToBatch(LogEvent logEvent, Instant now) {
        currentBatch.add(logEvent);
        return nextSendDelay(now);
    }

    private void execute() {
        LogEventBatch batch = takeCurrentBatch();
        if (!batch.isEmpty()) {
            try {
                processBatch(batch);
            } catch (Exception e) {
                LogEventStatus.getInstance().addFatal(this, "Failed to process batch", e);
            }
        }
    }

    protected void processBatch(LogEventBatch batch) {
        batchProcessor.processBatch(batch);
    }

    synchronized LogEventBatch takeCurrentBatch() {
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

    public LogEventBatchProcessor getBatchProcessor() {
        return batchProcessor;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{batchProcessor=" + this.batchProcessor + "}";
    }

    /**
     * Configure throttling for the provided marker. Throttling is a string like <code>PT10M PT30M</code>
     * indicating a list of periods (PT). In this example, after one message is sent, all messages
     * with this marker will be batched up for the next ten minutes (PT10M). If any messages were collected,
     * the next batch will be sent after 30 minutes (PT30M). The last throttling period will repeat
     * until a period passes with no batched messages.
     */
    public void configureMarkers(Configuration configuration) {
        for (String markerName : configuration.listProperties("markers")) {
            markerBatchers.put(MarkerFactory.getMarker(markerName),
                    createBatcher(configuration, markerName));
        }
    }

    protected BatchThrottler createBatcher(Configuration configuration, String markerName) {
        String throttle = configuration.getString("markers." + markerName + ".throttle");
        return new BatchThrottler(new ExecutorScheduler(scheduledExecutorService, shutdownHook), batchProcessor)
                .setThrottle(throttle);
    }

    BatchThrottler getMarker(Marker marker) {
        return markerBatchers.get(marker);
    }
}
