package org.logevents;

import org.logevents.observers.BatchingLogEventObserver;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.FileLogEventObserver;

/**
 * The main interface of the Log Event framework. Implement this
 * interface to process log events in your own way. The most used included
 * observers are {@link FileLogEventObserver} (used for file output),
 * {@link ConsoleLogEventObserver} (to standard out, with ANSI colors),
 * {@link CompositeLogEventObserver} (used to call multiple observers),
 * {@link CircularBufferLogEventObserver} (used to keep an internal buffer of log events)
 * and {@link BatchingLogEventObserver} (used to batch up multiple events before
 * processing).
 *
 * @author Johannes Brodwall.
 *
 */
public interface LogEventObserver {

    void logEvent(LogEvent logEvent);

}
