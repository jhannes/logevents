package org.logevents.observers.batch;

public interface LogEventBatchProcessor {

    void processBatch(LogEventBatch batch);

}
