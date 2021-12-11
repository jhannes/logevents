package org.logevents.benchmark.logevents;

import org.logevents.LogEventFactory;
import org.logevents.config.Configuration;
import org.logevents.formatters.PatternLogEventFormatter;
import org.logevents.observers.FileLogEventObserver;
import org.logevents.core.NullLogEventObserver;
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
import java.util.Map;

@State(Scope.Thread)
public class LogeventsFileBenchmark {

    protected Logger logger;

    private List<Object> arg1 = Arrays.asList(
            Instant.now(),
            new HashMap<>(),
            "Some string"
    );

    @Setup
    public void setup() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.file.lockOnWrite", "false");
        Configuration configuration = new Configuration(properties, "observer.file");
        FileLogEventObserver observer = new FileLogEventObserver(
                configuration,
                "target/benchmark/logevents/%node/%application-%mdc{user:-anon}-%date{HHmm}.log",
                java.util.Optional.of(new PatternLogEventFormatter("[%coloredLevel] [%file:%line] (%mdc) %message"))
        );
        LogEventFactory factory = LogEventFactory.getInstance();
        logger = factory.getLogger(getClass().getName());

        factory.setRootObserver(new NullLogEventObserver());
        factory.setLevel(logger, Level.INFO);
        factory.setObserver(logger, observer, true);
    }

    @Benchmark
    public void singleOutputToFile() {
        //MDC.put("user", "user1");
        logger.info("This is a test with values={}", arg1);
    }

    @Benchmark
    public void multiOutputToFile() {
        //MDC.put("user", "other");
        logger.info("This is a test with values={}", arg1);
        logger.warn("This is another value");
    }

    @Benchmark
    public void logBelowThreshold() {
        MDC.put("user", "JohaNNES");
        logger.debug("This is a test with values={}", arg1);
    }

    @Benchmark
    public void guardedLogBelowThreshold() {
        MDC.put("user", "JohaNNES");
        if (logger.isDebugEnabled()) {
            logger.debug("This is a test with values={}", arg1);
        }
    }

    public static void main(String[] args) {
        LogeventsFileBenchmark benchmark = new LogeventsFileBenchmark();
        benchmark.setup();
        benchmark.multiOutputToFile();
    }

}
