package org.logevents.benchmark.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@State(Scope.Thread)
public class LogbackFileBenchmark {

    protected Logger logger;

    private List<Object> arg1 = Arrays.asList(
            Instant.now(),
            new HashMap<>(),
            "Some string"
    );

    @Setup
    public void setup() {
        LoggerContext context = new LoggerContext();

        RollingFileAppender file = new RollingFileAppender();
        file.setFile("target/benchmark/logback/logfile.log");
        file.setImmediateFlush(true);
        file.setContext(context);
        LayoutWrappingEncoder encoder = new LayoutWrappingEncoder();
        encoder.setContext(context);
        PatternLayout layout = new PatternLayout();
        layout.setPattern("[%level] [%file:%line] %mdc %message %n");
        layout.setContext(context);
        layout.start();
        encoder.setLayout(layout);
        encoder.start();
        file.setEncoder(encoder);
        TimeBasedRollingPolicy policy = new TimeBasedRollingPolicy();
        policy.setContext(context);
        policy.setParent(file);
        policy.setFileNamePattern("target/benchmark/logback/logfile-%d.log");
        policy.start();
        file.setTriggeringPolicy(policy);
        file.start();


        ch.qos.logback.classic.Logger logger = context.getLogger(getClass());
        logger.addAppender(file);
        logger.setLevel(Level.INFO);

        this.logger = logger;
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
        LogbackFileBenchmark benchmark = new LogbackFileBenchmark();
        benchmark.setup();
        benchmark.singleOutputToFile();
    }
}
