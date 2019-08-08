package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.observers.batch.BatchThrottler;
import org.logevents.observers.batch.CooldownBatcher;
import org.logevents.observers.batch.ExecutorScheduler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventShutdownHook;
import org.logevents.observers.batch.Scheduler;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Used to gather up a number of log event to process as a batch. This is useful
 * when using logging destinations where high frequency of messages would be
 * inefficient or noisy, such as email or Slack and for logging asynchronously to
 * external systems over network such as databases or Elasticsearch.
 * <p>
 * {@link BatchingLogEventObserver} can decide how to batch events with batchers,
 * for example {@link BatchThrottler} and {@link CooldownBatcher}. When processing,
 * the {@link BatchingLogEventObserver} calls {@link #processBatch(LogEventBatch)}
 * with the whole batch to process the log events, which should be overridden by
 * subclasses. Consecutive Log events with the same message pattern and log level
 * are grouped together so the processor can easily ignore duplicate messages.
 *
 * @see SlackLogEventObserver
 * @see MicrosoftTeamsLogEventObserver
 * @see SmtpLogEventObserver
 * @see ElasticsearchLogEventObserver
 * @see DatabaseLogEventObserver
 *
 * @author Johannes Brodwall
 */
public abstract class BatchingLogEventObserver extends FilteredLogEventObserver {

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

    protected static LogEventShutdownHook shutdownHook = new LogEventShutdownHook();
    static {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private Map<Marker, BatchThrottler> markerBatchers = new HashMap<>();
    private CooldownBatcher defaultBatcher;
    protected Supplier<Scheduler> flusherFactory;

    public BatchingLogEventObserver() {
        this(() -> new ExecutorScheduler(scheduledExecutorService, shutdownHook));
    }

    public BatchingLogEventObserver(Supplier<Scheduler> flusherFactory) {
        this.flusherFactory = flusherFactory;
        defaultBatcher = new CooldownBatcher(flusherFactory.get(), this::processBatch);
    }

    /**
     * Read <code>idleThreshold</code>, <code>cooldownTime</code> and <code>maximumWaitTime</code>
     * from configuration
     */
    protected void configureBatching(Configuration configuration) {
        defaultBatcher.configure(configuration);
    }

    @Override
    protected final void doLogEvent(LogEvent logEvent) {
        getBatcher(logEvent).logEvent(logEvent);
    }

    protected LogEventObserver getBatcher(LogEvent logEvent) {
        if (logEvent.getMarker() != null) {
            for (Marker marker : markerBatchers.keySet()) {
                if (marker.contains(logEvent.getMarker())) {
                    return markerBatchers.get(marker);
                }
            }
        }
        return defaultBatcher;
    }

    protected abstract void processBatch(LogEventBatch batch);

    @Override
    public String toString() {
        return getClass().getSimpleName();
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
            markerBatchers.put(MarkerFactory.getMarker(markerName), createBatcher(configuration, markerName));
        }
    }

    protected BatchThrottler createBatcher(Configuration configuration, String markerName) {
        String throttle = configuration.getString("markers." + markerName + ".throttle");
        return new BatchThrottler(flusherFactory.get(), this::processBatch).setThrottle(throttle);
    }

    BatchThrottler getMarker(Marker marker) {
        return markerBatchers.get(marker);
    }
}
