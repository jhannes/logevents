package org.logevents;

import java.io.IOException;

import org.logevents.destinations.ConsoleLogEventDestination;
import org.logevents.destinations.ConsoleLogEventFormatter;
import org.logevents.destinations.DateRollingFileDestination;
import org.logevents.destinations.LogEventFormatter;
import org.logevents.observers.BatchingLogEventObserver;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.LevelThresholdConditionalObserver;
import org.logevents.observers.TextLogEventObserver;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.impl.StaticLoggerBinder;

public class LogEventConfiguration {

    private LogEventFactory factory = StaticLoggerBinder.getSingleton().getLoggerFactory();

    public void setObserver(LogEventObserver observer) {
        setObserver(factory.getRootLogger(), observer, false);
    }

    public void setObserver(Logger logger, LogEventObserver observer, boolean inheritParentObserver) {
        factory.setObserver(logger, observer, inheritParentObserver);
    }

    public void reset() {
        factory.reset();
    }

    public void setLevel(Level level) {
        setLevel(factory.getRootLogger(), level);
    }

    public void setLevel(String string, Level level) {
        setLevel(factory.getLogger(string), level);
    }

    public void setLevel(Logger logger, Level level) {
        factory.setLevel(logger, level);
    }

    public static LogEventObserver consoleObserver() {
        return consoleObserver(new ConsoleLogEventFormatter());
    }

    public static LogEventObserver consoleObserver(LogEventFormatter formatter) {
        return new TextLogEventObserver(new ConsoleLogEventDestination(), formatter);
    }

    public LogEventObserver dateRollingAppender(String logName) throws IOException {
        return new TextLogEventObserver(new DateRollingFileDestination(logName), LogEventFormatter.withDefaultFormat());
    }

    public LogEventObserver combine(LogEventObserver... observers) {
        return CompositeLogEventObserver.combine(observers);
    }

    public BatchingLogEventObserver batchEventObserver(LogEventBatchProcessor batchProcessor) {
        return new BatchingLogEventObserver(batchProcessor);
    }

    public static LogEventObserver levelThresholdObserver(Level threshold, LogEventObserver delegate) {
        return new LevelThresholdConditionalObserver(threshold, delegate);
    }


}
