package org.logevents.benchmark.analyze;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.PatternLogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class FormatBenchmark {

    private LogEventFormatter baseline = logEvent -> logEvent.getMessage();

    private LogEventFormatter logeventsComplexFormatter = new PatternLogEventFormatter("[%coloredLevel] [%file:%line] (%mdc) %message");

    private LogEventFormatter logeventsSimpleFormatter = new PatternLogEventFormatter("[%level] [%file:%line] %mdc %message");

    private LogEventFormatter logeventsPlainFormatter = new TTLLEventLogFormatter();

    private PatternLayout logbackLayout;
    private Logger logger;
    private LogEvent logEvent;
    private LoggingEvent loggingEvent;

    @Setup
    public void setup() {
        LoggerContext context = new LoggerContext();
        logbackLayout = new PatternLayout();
        logbackLayout.setPattern("[%level] [%file:%line] %mdc %message %n");
        logbackLayout.setContext(context);
        logbackLayout.start();
        logger = context.getLogger(FormatBenchmark.class);

        logEvent = new LogEventSampler().build();

        loggingEvent = new LoggingEvent(
                Logger.class.getName(),
                logger,
                Level.WARN,
                LogEventSampler.randomString(),
                null, new Object[0]);
    }

    @Benchmark
    public void baseline() {
        baseline.apply(logEvent);
    }

    @Benchmark
    public void logeventsComplex() {
        logeventsComplexFormatter.apply(logEvent);
    }

    @Benchmark
    public void logeventsSimple() {
        logeventsSimpleFormatter.apply(logEvent);
    }

    @Benchmark
    public void logeventsPlain() {
        logeventsPlainFormatter.apply(logEvent);
    }

    @Benchmark
    public void logback() {
        logbackLayout.doLayout(loggingEvent);
    }
}
