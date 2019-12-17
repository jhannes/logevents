package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class LogEventBatcherWithMdc implements LogEventObserver {
    protected final Marker marker;
    protected final String mdcKey;
    private LogEventBatcher defaultBatcher;
    private Map<String, LogEventBatcher> mdcBatches = new HashMap<>();
    private BatcherFactory batcherFactory;
    private Consumer<List<LogEvent>> processor;

    public LogEventBatcherWithMdc(BatcherFactory batcherFactory, String markerName, String mdcKey, Consumer<List<LogEvent>> processor) {
        this.batcherFactory = batcherFactory;
        this.mdcKey = mdcKey;
        this.processor = processor;
        this.marker = MarkerFactory.getMarker(markerName);
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
        return new LogEventBatcher(batcherFactory.createBatcher(processor));
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
