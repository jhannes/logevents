package org.logevents.query;

import org.logevents.LogEventObserver;

public interface LogEventSource extends LogEventObserver {
    LogEventQueryResult query(LogEventFilter filter);
}
