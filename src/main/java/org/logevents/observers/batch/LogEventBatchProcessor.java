package org.logevents.observers.batch;

import java.util.List;

public interface LogEventBatchProcessor {

    void processBatch(List<LogEventGroup> batch);

}
