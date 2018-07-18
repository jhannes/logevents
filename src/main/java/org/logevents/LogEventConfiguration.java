package org.logevents;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.logevents.destinations.ConsoleLogEventDestination;
import org.logevents.destinations.ConsoleLogEventFormatter;
import org.logevents.destinations.DateRollingFileDestination;
import org.logevents.destinations.LogEventFormatter;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.TextLogEventObserver;
import org.logevents.observers.batch.BatchingLogEventObserver;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.impl.StaticLoggerBinder;

public class LogEventConfiguration {

    private LogEventFactory factory = StaticLoggerBinder.getSingleton().getLoggerFactory();
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3,
            new ThreadFactory() {
                private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = defaultFactory.newThread(r);
                    thread.setName("LogEvent$ScheduleExecutor-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    public void configure() {
        factory.setLevel(factory.getRootLogger(), Level.WARN);
        factory.setObserver(factory.getRootLogger(), consoleObserver(LogEventFormatter.withDefaultFormat()), false);
    }

    public void setObserver(LogEventObserver observer) {
        setObserver(factory.getRootLogger(), observer, false);
    }

    public void setObserver(String logName, LogEventObserver observer, boolean inheritParentObserver) {
        setObserver(factory.getLogger(logName), observer, inheritParentObserver);
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
        return new BatchingLogEventObserver(batchProcessor, scheduledExecutorService);
    }

    public static LogEventObserver levelThresholdObserver(Level threshold, LogEventObserver logEventObserver) {
        return event -> {
            if (threshold.toInt() <= event.getLevel().toInt()) {
                logEventObserver.logEvent(event);
            }
        };
    }


}
