package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;

import java.util.List;

/**
 * Batches up received messages to limit noise. Will throttle the processing speed
 * according to a Batcher.
 *
 * @see CooldownBatcher
 * @see ThrottlingBatcher
 */
public class LogEventBatcher implements LogEventObserver {
    private Batcher<LogEvent> batcher;

    public LogEventBatcher(Batcher<LogEvent> batcher) {
        this.batcher = batcher;
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        batcher.accept(logEvent);
    }

    public List<LogEvent> getCurrentBatch() {
        return batcher.getCurrentBatch();
    }
}
