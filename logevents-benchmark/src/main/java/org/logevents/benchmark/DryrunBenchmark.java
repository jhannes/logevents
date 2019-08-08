package org.logevents.benchmark;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class DryrunBenchmark {
    public static void main(String[] args) throws RunnerException {
        Collection<RunResult> results = new Runner(new OptionsBuilder()
                .measurementTime(TimeValue.milliseconds(100))
                .measurementIterations(3)
                .warmupTime(TimeValue.microseconds(5))
                .warmupIterations(1)
                .forks(1)
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .shouldFailOnError(true)
                //.include("\\.singleOutputToFile")
                .build()).run();

        System.err.println(results);
    }
}
