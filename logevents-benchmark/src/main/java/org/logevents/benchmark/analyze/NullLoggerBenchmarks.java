package org.logevents.benchmark.analyze;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.helpers.NOPAppender;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.slf4j.Logger;
import org.slf4j.MDC;

@State(Scope.Benchmark)
public class NullLoggerBenchmarks {

    private Logger logeventsLogger;
    private Logger logbackLogger;

    @Setup
    public void setupLogevents() {
        LogEventFactory factory = new LogEventFactory();
        Logger logger = factory.getLogger(NullLoggerBenchmarks.class.getName());
        factory.setObserver(logger, logEvent -> { }, false);
        this.logeventsLogger = logger;
    }


    @Benchmark
    public void logEvents() {
        MDC.put("test", "foo");
        logeventsLogger.info("Hello {}", "WORLD");
    }

    @Benchmark
    public void logEventsHidden() {
        MDC.put("test", "foo");
        logeventsLogger.trace("Hello {}", "WORLD");
    }

    @Setup
    public void setupLogback() {
        LoggerContext context = new LoggerContext();
        ch.qos.logback.classic.Logger logger = context.getLogger(getClass());
        logger.addAppender(new NOPAppender<>());
        logger.setLevel(Level.INFO);

        this.logbackLogger = logger;
        System.out.println("Hello");
    }

    @Benchmark
    public void logback() {
        MDC.put("test", "foo");
        logbackLogger.info("Hello {}", "WORLD");
    }

    @Benchmark
    public void logbackHidden() {
        MDC.put("test", "foo");
        logbackLogger.trace("Hello {}", "WORLD");
    }


}
