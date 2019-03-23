package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.status.LogEventStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BatchThrottler {
    private final Scheduler executor;
    private List<Duration> throttle = new ArrayList<>();
    private int throttleIndex = 0;
    private LogEventBatch currentBatch = new LogEventBatch();
    private LogEventBatchProcessor batchProcessor;
    private Instant lastFlush = Instant.ofEpochMilli(0);

    public BatchThrottler(Scheduler executor, LogEventBatchProcessor batchProcessor) {
        this.executor = executor;
        this.executor.setAction(this::flush);
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

    public void setBatchProcessor(LogEventBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
    }

    public void logEvent(LogEvent logEvent) {
        doLogEvent(logEvent, Instant.now());
    }

    private synchronized  void doLogEvent(LogEvent logEvent, Instant now) {
        if (currentBatch.isEmpty()) {
            // schedule for execution
            Instant nextFlush = lastFlush.plusMillis(throttle.get(throttleIndex).toMillis());
            if (now.isAfter(nextFlush)) {
                throttleIndex = 0;
            }
            executor.schedule(throttle.get(throttleIndex));
            throttleIndex = Math.min(throttleIndex+1, throttle.size()-1);
        }
        currentBatch.add(logEvent);
    }

    synchronized void flush() {
        lastFlush = Instant.now();
        LogEventBatch batchToSend = this.currentBatch;
        currentBatch = new LogEventBatch();
        LogEventStatus.getInstance().addTrace(this,"flush " + batchToSend.size() + " messages");
        if (batchToSend.isEmpty()) {
            throttleIndex = 0;
        } else {
            batchProcessor.processBatch(batchToSend);
        }
    }

}
