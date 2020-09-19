package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.observers.batch.BatcherFactory;
import org.logevents.observers.batch.CooldownBatcher;
import org.logevents.observers.batch.CooldownBatcherFactory;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatcher;
import org.logevents.observers.batch.LogEventBatcherWithMdc;
import org.logevents.observers.batch.LogEventShutdownHook;
import org.logevents.observers.batch.ThrottleBatcherFactory;
import org.logevents.observers.batch.ThrottlingBatcher;
import org.logevents.util.DaemonThreadFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Used to gather up a number of log event to process as a batch. This is useful
 * when using logging destinations where high frequency of messages would be
 * inefficient or noisy, such as email or Slack and for logging asynchronously to
 * external systems over network such as databases or Elasticsearch.
 * <p>
 * {@link AbstractBatchingLogEventObserver} can decide how to batch events with batchers,
 * for example {@link ThrottlingBatcher} and {@link CooldownBatcher}. When processing,
 * the {@link AbstractBatchingLogEventObserver} calls {@link #processBatch(List)}
 * with the whole batch to process the log events, which should be overridden by
 * subclasses. Consecutive Log events with the same message pattern and log level
 * are grouped together so the processor can easily ignore duplicate messages.
 *
 * <h3>Configuration example with cooldown (don't send messages too frequently)</h3>
 *
 *  The following will send the send messages at level WARN that don't have the {@link Marker}
 *  "PERSONAL". After each batch, wait at least 10 seconds before sending the next batch. But after
 *  each message is received, wait 2 seconds to see if more messages are coming. In any case, never
 *  wait more than 30 seconds before sending a batch.
 *
 * <pre>
 * observer.sample.threshold=WARN
 * observer.sample.suppressMarkers=PERSONAL_DATA
 * observer.sample.idleThreshold=PT2S
 * observer.sample.cooldownTime=PT10S
 * observer.sample.maximumWaitTime=PT30S
 * </pre>
 *
 * <h3>Configuration example with throttle (increasingly larger offsets)</h3>
 *
 *  The following will send the first message at level WARN with the {@link Marker} "DAEMON" immediately,
 *  then wait at least 30 seconds before sending the next, then 5 minutes, then 15 minutes between each
 *  batch.
 *
 * <pre>
 * observer.sample.threshold=WARN
 * observer.sample.requireMarker=DAEMON
 * observer.sample.throttle=PT30S PT5M PT15M
 * </pre>
 *
 * <h3>Marker-specific configuration</h3>
 *
 * The following will throttle messages with MY_MARKER, grouped by MDC value userId. If there is a log
 * event with an unused userId, it will be sent immediately. Then all events for the next minute
 * <em>for the same userId</em> will be collected, then the next hour and then the next day. If there
 * are log events for other userIds, they will be batched separately. This is useful for situations like
 * API batch users where the credentials have expired, for example.
 *
 * <pre>
 * observer.sample.markers.MY_MARKER.throttle=PT1M PT1H PT24H
 * observer.sample.markers.MY_MARKER.mdc=userId
 * </pre>
 *
 * @see SlackLogEventObserver
 * @see MicrosoftTeamsLogEventObserver
 * @see SmtpLogEventObserver
 * @see ElasticsearchLogEventObserver
 * @see DatabaseLogEventObserver
 *
 * @author Johannes Brodwall
 */
public abstract class AbstractBatchingLogEventObserver extends AbstractFilteredLogEventObserver {

    protected static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3,
            new DaemonThreadFactory("BatchingLogEventObserver", 3));

    protected static LogEventShutdownHook shutdownHook = new LogEventShutdownHook();
    static {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private final Map<Marker, LogEventObserver> markerBatchers = new HashMap<>();
    protected LogEventObserver defaultBatcher;
    protected ScheduledExecutorService executor;

    public AbstractBatchingLogEventObserver() {
        this(scheduledExecutorService);
    }

    public AbstractBatchingLogEventObserver(ScheduledExecutorService executor) {
        defaultBatcher = new LogEventBatcher(new CooldownBatcherFactory(executor, shutdownHook).createBatcher(this::processBatch));
        this.executor = executor;
    }

    /**
     * Read <code>idleThreshold</code>, <code>cooldownTime</code> and <code>maximumWaitTime</code>
     * from configuration
     */
    protected void configureBatching(Configuration configuration) {
        this.defaultBatcher = new LogEventBatcher(getBatcherFactory(configuration, "").createBatcher(this::processBatch));
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
        BatcherFactory batcherFactory = getBatcherFactory(configuration, "markers." + markerName + ".");
        return configuration.optionalString("markers." + markerName + ".mdc")
                .map(mdcKey -> createMdcBatcher(batcherFactory, configuration, markerName, configuration.getString("markers." + markerName + ".mdc")))
                .orElseGet(() -> new LogEventBatcher(batcherFactory.createBatcher(createProcessor(configuration, markerName))));
    }

    protected Consumer<List<LogEvent>> createProcessor(Configuration configuration, String markerName) {
        return this::processBatch;
    }

    protected BatcherFactory getBatcherFactory(Configuration configuration, String prefix) {
        return configuration.optionalString(prefix + "throttle")
                .map(t -> (BatcherFactory)new ThrottleBatcherFactory(executor, shutdownHook, t))
                .orElseGet(() -> new CooldownBatcherFactory(executor, shutdownHook, configuration, prefix));
    }

    protected LogEventObserver createMdcBatcher(BatcherFactory batcherFactory, Configuration configuration, String markerName, String mdcKey) {
        return new LogEventBatcherWithMdc(batcherFactory, markerName, mdcKey, this::processBatch);
    }

    protected LogEventObserver getMarkerBatcher(Marker myMarker) {
        return markerBatchers.get(myMarker);
    }

    protected LogEventObserver getDefaultBatcher() {
        return defaultBatcher;
    }
}
