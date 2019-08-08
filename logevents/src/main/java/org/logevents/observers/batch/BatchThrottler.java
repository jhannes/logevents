package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.status.LogEventStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Batches up received messages to limit noise. Will throttle the processing speed
 * according to a list of throttles. E.g. PT1M PT10M PT30M will process the first log event
 * immediately, then batch up any log events for the next minute. If any log events
 * were received during this interval, then all log events for the next ten minutes will
 * be batched. Resets back to processing immediately if there were no log events in an
 * interval.
 */
public class BatchThrottler implements LogEventObserver {
    private final Scheduler executor;
    private List<Duration> throttle = new ArrayList<>();
    private int throttleIndex = 0;
    private LogEventBatch currentBatch = new LogEventBatch();
    private Consumer<LogEventBatch> batchProcessor;
    private Instant lastFlushTime = Instant.ofEpochMilli(0);

    public BatchThrottler(Scheduler executor, Consumer<LogEventBatch> batchProcessor) {
        this.executor = executor;
        this.executor.setAction(this::execute);
        this.batchProcessor = batchProcessor;
    }

    public BatchThrottler setThrottle(String throttleConfiguration) {
        this.throttle.clear();
        this.throttle.add(Duration.ZERO);
        Stream.of(throttleConfiguration.split(" |,\\*"))
                .map(Duration::parse)
                .forEach(throttle::add);
        return this;
    }

    public BatchThrottler setThrottle(List<Duration> durations) {
        this.throttle.clear();
        this.throttle.add(Duration.ZERO);
        this.throttle.addAll(durations);
        this.throttleIndex = 0;
        return this;
    }

    public void setBatchProcessor(Consumer<LogEventBatch> batchProcessor) {
        this.batchProcessor = batchProcessor;
    }

    @Override
    public synchronized void logEvent(LogEvent logEvent) {
        if (currentBatch.isEmpty()) {
            // schedule for execution
            Instant nextFlush = lastFlushTime.plusMillis(throttle.get(throttleIndex).toMillis());
            if (Instant.now().isAfter(nextFlush)) {
                throttleIndex = 0;
            }
            executor.scheduleFlush(throttle.get(throttleIndex));
            throttleIndex = Math.min(throttleIndex+1, throttle.size()-1);
        }
        currentBatch.add(logEvent);
    }

    protected synchronized void execute() {
        LogEventBatch batchToSend = takeCurrentBatch();
        LogEventStatus.getInstance().addTrace(this, "flush " + batchToSend.size() + " messages");
        batchProcessor.accept(batchToSend);
    }

    protected LogEventBatch takeCurrentBatch() {
        lastFlushTime = Instant.now();
        LogEventBatch returnedBatch = this.currentBatch;
        currentBatch = new LogEventBatch();
        if (returnedBatch.isEmpty()) {
            throttleIndex = 0;
        }
        return returnedBatch;
    }
}
