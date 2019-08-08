package org.logevents.benchmark.logevents;

import org.logevents.LogEventFactory;
import org.logevents.formatting.PatternLogEventFormatter;
import org.logevents.observers.FileLogEventObserver;
import org.logevents.observers.NullLogEventObserver;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@State(Scope.Thread)
public class LogeventsSimpleFileBenchmark {

    protected Logger logger;

    private List<Object> arg1 = Arrays.asList(
            Instant.now(),
            new HashMap<>(),
            "Some string"
    );

    @Setup
    public void setup() {
        FileLogEventObserver observer = new FileLogEventObserver(
                "target/benchmark/logevents/%application-%date{HHmm}.log",
                new PatternLogEventFormatter("[%level] [%file:%line] %mdc %message")
        );
        LogEventFactory factory = LogEventFactory.getInstance();
        logger = factory.getLogger(getClass().getName());

        factory.setRootObserver(new NullLogEventObserver());
        factory.setLevel(logger, Level.INFO);
        factory.setObserver(logger, observer, true);
    }

    @Benchmark
    public void singleOutputToFile() {
        MDC.put("user", "user1");
        logger.info("This is a test with values={}", arg1);
    }

    @Benchmark
    public void multiOutputToFile() {
        MDC.put("user", "other");
        logger.info("This is a test with values={}", arg1);
        logger.warn("This is another value");
    }
}
