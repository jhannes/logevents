package org.logevents.extend.servlets;

import org.logevents.LogEvent;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LogEventSampler {
    private static Random random = new Random();
    private String loggerName = LogEventSampler.class.getName();
    private Level level = Level.WARN;
    private String threadName = sampleThreadName();
    private Instant timestamp = Instant.now();
    private Marker marker = null;
    private Optional<String> format = Optional.empty();
    private Object[] args = sampleArgs();
    private Map<String, String> mdc = new HashMap<>();

    public LogEvent build() {
        String format = this.format.orElseGet(() -> sampleMessage(args));
        return new LogEvent(loggerName, level, threadName, timestamp, marker, format, args, mdc);
    }

    private Object[] sampleArgs() {
        return new Object[0];
    }

    private static String sampleThreadName() {
        return "Thread-" + pickOne("one", "two", "three", "four", "five", "six") + "-" + random.nextInt(1000);
    }

    private static <T> T pickOne(T... alternatives) {
        return alternatives[random.nextInt(alternatives.length)];
    }


    private String sampleMessage(Object[] args) {
        int length = args.length;
        if (length > 0 && args[length-1] instanceof Throwable) length--;
        String ending = IntStream.range(0, length).mapToObj(i -> "{}").collect(Collectors.joining(" "));
        return "Here is a " + level + " test message of " + random.nextInt(100) + " with " + ending;
    }

    public LogEventSampler withMarker(Marker marker) {
        this.marker = marker;
        return this;
    }

    public LogEventSampler withLevel(Level level) {
        this.level = level;
        return this;
    }

    public LogEventSampler withTime(ZonedDateTime time) {
        this.timestamp = time.toInstant();
        return this;
    }

    public LogEventSampler withThread(String thread) {
        this.threadName = thread;
        return this;
    }

    public LogEventSampler withTime(Instant instant) {
        this.timestamp = instant;
        return this;
    }

    public LogEventSampler withMdc(String name, String value) {
        mdc.put(name, value);
        return this;
    }
}
