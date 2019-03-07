package org.logevents.observers.batch;

import java.util.Map;

public interface JsonLogEventsBatchFormatter {
    Map<String, Object> createMessage(LogEventBatch batch);
}
