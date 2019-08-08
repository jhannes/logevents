package org.logevents.benchmark.analyze;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

public class QuickrunAnalyze {
    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .measurementTime(TimeValue.milliseconds(500))
                .measurementIterations(3)
                .warmupTime(TimeValue.milliseconds(50))
                .warmupIterations(2)
                .forks(3)
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .shouldFailOnError(true)
                .include("^org.logevents.benchmark.analyze.Fileoutput")
                .build()
        ).run();
    }
}
