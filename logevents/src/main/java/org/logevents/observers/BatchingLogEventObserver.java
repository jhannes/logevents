package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.observers.batch.CooldownBatcher;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatcher;
import org.logevents.observers.batch.LogEventBatcherWithMdc;
import org.logevents.observers.batch.LogEventShutdownHook;
import org.logevents.observers.batch.ThrottlingBatcher;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used to gather up a number of log event to process as a batch. This is useful
 * when using logging destinations where high frequency of messages would be
 * inefficient or noisy, such as email or Slack and for logging asynchronously to
 * external systems over network such as databases or Elasticsearch.
 * <p>
 * {@link BatchingLogEventObserver} can decide how to batch events with batchers,
 * for example {@link ThrottlingBatcher} and {@link CooldownBatcher}. When processing,
 * the {@link BatchingLogEventObserver} calls {@link #processBatch(List)}
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

    private Map<Marker, LogEventObserver> markerBatchers = new HashMap<>();
    private LogEventObserver defaultBatcher;
    protected ScheduledExecutorService executor;

    public BatchingLogEventObserver() {
        this(scheduledExecutorService);
    }

    public BatchingLogEventObserver(ScheduledExecutorService executor) {
        defaultBatcher = new LogEventBatcher(new CooldownBatcher<>(this::processBatch, executor));
        this.executor = executor;
    }

    /**
     * Read <code>idleThreshold</code>, <code>cooldownTime</code> and <code>maximumWaitTime</code>
     * from configuration
     */
    protected void configureBatching(Configuration configuration) {
        this.defaultBatcher = new LogEventBatcher(createCooldownBatcher(configuration, ""));
    }

    @Override
    protected final void doLogEvent(LogEvent logEvent) {
        getBatcher(logEvent).logEvent(logEvent);
    }

    protected LogEventObserver getBatcher(LogEvent logEvent) {
        if (logEvent.getMarker() != null) {
            if (markerBatchers.containsKey(logEvent.getMarker())) {
                return markerBatchers.get(logEvent.getMarker());
            }
            Iterator<Marker> iterator = logEvent.getMarker().iterator();
            while (iterator.hasNext()) {
                Marker next = iterator.next();
                if (markerBatchers.containsKey(next)) {
                    return markerBatchers.get(next);
                }
            }
        }
        return defaultBatcher;
    }

    protected void processBatch(List<LogEvent> batch) {
        processBatch(new LogEventBatch(batch));
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

    protected LogEventObserver createBatcher(Configuration configuration, String markerName) {
        if (configuration.optionalString("markers." + markerName + ".mdc").isPresent()) {
            return createMdcBatcher(configuration, markerName);
        }
        return configuration.optionalString("markers." + markerName + ".throttle")
                .map(t -> new LogEventBatcher(new ThrottlingBatcher<>(t, this::processBatch, executor)))
                .orElseGet(() -> new LogEventBatcher(createCooldownBatcher(configuration, "markers." + markerName)));
    }

    private CooldownBatcher<LogEvent> createCooldownBatcher(Configuration configuration, String prefix) {
        CooldownBatcher<LogEvent> batcher = new CooldownBatcher<>(this::processBatch, executor);
        batcher.configure(configuration, prefix);
        return batcher;
    }

    protected LogEventObserver createMdcBatcher(Configuration configuration, String markerName) {
        return new LogEventBatcherWithMdc(executor, configuration, markerName, this::processBatch);
    }

    protected LogEventObserver getMarkerBatcher(Marker myMarker) {
        return markerBatchers.get(myMarker);
    }

    protected LogEventObserver getDefaultBatcher() {
        return defaultBatcher;
    }
}
