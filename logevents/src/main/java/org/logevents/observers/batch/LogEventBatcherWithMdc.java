package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class LogEventBatcherWithMdc implements LogEventObserver {
    protected final Marker marker;
    protected final String mdcKey;
    protected final Optional<Duration> idleThreshold;
    protected final Optional<Duration> cooldownTime;
    protected final Optional<Duration> maximumWaitTime;
    protected final String flushSchedule;
    private LogEventBatcher defaultBatcher;
    private Map<String, LogEventBatcher> mdcBatches = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private Consumer<List<LogEvent>> processor;

    public LogEventBatcherWithMdc(ScheduledExecutorService executor, String flushSchedule, Marker marker, String mdcKey, Consumer<List<LogEvent>> processor) {
        this.processor = processor;
        this.scheduler = executor;
        this.flushSchedule = flushSchedule;
        this.idleThreshold = Optional.empty();
        this.maximumWaitTime = Optional.empty();
        this.cooldownTime = Optional.empty();
        this.marker = marker;
        this.mdcKey = mdcKey;
        this.defaultBatcher = createMdcBatcher(null, processor);
    }

    public LogEventBatcherWithMdc(ScheduledExecutorService executor, Configuration configuration, String markerName, Consumer<List<LogEvent>> processor) {
        this.processor = processor;
        this.marker = MarkerFactory.getMarker(markerName);
        this.scheduler = executor;
        this.flushSchedule = configuration.optionalString("markers." + markerName + ".throttle").orElse(null);
        this.idleThreshold = configuration.optionalString("markers." + markerName + ".idleThreshold").map(Duration::parse);
        this.cooldownTime = configuration.optionalString("markers." + markerName + ".cooldownTime").map(Duration::parse);
        this.maximumWaitTime = configuration.optionalString("markers." + markerName + ".maximumWaitTime").map(Duration::parse);
        this.mdcKey = configuration.getString("markers." + markerName + ".mdc");
        this.defaultBatcher = createMdcBatcher(null, processor);
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        getBatcher(logEvent).logEvent(logEvent);
    }

    private synchronized LogEventObserver getBatcher(LogEvent logEvent) {
        String key = logEvent.getMdc(mdcKey, null);
        if (key == null) {
            return defaultBatcher;
        }
        return mdcBatches.computeIfAbsent(key,
                mdcValue -> createMdcBatcher(mdcValue, batch -> {
                    if (batch.isEmpty()) {
                        removeBatch(mdcValue);
                    }
                    processor.accept(batch);
                }));
    }

    protected LogEventBatcher createMdcBatcher(
            String mdcValue,
            Consumer<List<LogEvent>> processor
    ) {
        if (flushSchedule != null) {
            return new LogEventBatcher(new ThrottlingBatcher<>(flushSchedule, processor, scheduler));
        } else {
            CooldownBatcher<LogEvent> batcher = new CooldownBatcher<>(processor, scheduler);
            idleThreshold.ifPresent(batcher::setIdleThreshold);
            maximumWaitTime.ifPresent(batcher::setMaximumWaitTime);
            cooldownTime.ifPresent(batcher::setCooldownTime);
            return new LogEventBatcher(batcher);
        }
    }

    private synchronized void removeBatch(String mdcValue) {
        mdcBatches.remove(mdcValue);
    }

    public LogEventBatcher getBatcher(String mdcValue) {
        return mdcBatches.get(mdcValue);
    }

    public List<LogEvent> getCurrentBatch() {
        return defaultBatcher.getCurrentBatch();
    }
}
