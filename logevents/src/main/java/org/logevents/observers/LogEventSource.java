package org.logevents.observers;

import org.logevents.LogEventObserver;
import org.logevents.query.LogEventQuery;
import org.logevents.query.LogEventQueryResult;

public interface LogEventSource extends LogEventObserver {
    LogEventQueryResult query(LogEventQuery query);
}
