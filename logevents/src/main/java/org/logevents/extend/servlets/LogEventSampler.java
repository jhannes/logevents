package org.logevents.extend.servlets;

import org.logevents.LogEvent;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LogEventSampler {
    private static final Marker[] SAMPLE_MARKERS = new Marker[] {
            MarkerFactory.getMarker("AUDIT"), MarkerFactory.getMarker("SECURITY"),
            MarkerFactory.getMarker("LIFECYCLE"), MarkerFactory.getMarker("OPS")
    };
    private static Random random = new Random();

    public static String sampleLoggerName() {
        return pickOne("com", "org", "net")
                + ".example."
                + pickOne("myapp", "app", "superapp")
                + "."
                + pickOne("Customer", "Order", "Person") + pickOne("Controller", "Service", "Repository");
    }

    private String threadName = sampleThreadName();
    private Optional<String> loggerName = Optional.empty();
    private Optional<Level> level = Optional.empty();
    private Instant timestamp = Instant.now();
    private Marker marker = sampleMarker();
    private Optional<String> format = Optional.empty();
    private Object[] args = sampleArgs();
    private Map<String, String> mdc = new LinkedHashMap<>();

    public LogEvent build() {
        return new LogEvent(
                loggerName.orElseGet(LogEventSampler::sampleLoggerName),
                level.orElseGet(() -> pickOne(Level.INFO, Level.WARN, Level.ERROR)),
                threadName,
                timestamp,
                marker,
                this.format.orElseGet(() -> sampleMessage(args)),
                args,
                mdc);
    }

    public static Marker sampleMarker() {
        return random.nextInt(100) < 30 ? pickOne(SAMPLE_MARKERS) : null;
    }

    private Object[] sampleArgs() {
        return new Object[0];
    }

    private static String sampleThreadName() {
        return "Thread-" + randomString();
    }

    public static String randomString() {
        return pickOne("one", "two", "three", "four", "five", "six") + "-" + random.nextInt(1000);
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

    public LogEventSampler withMarker() {
        return withMarker(MarkerFactory.getMarker(pickOne("FIRST", "SECOND", "THIRD")));
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

    public LogEventSampler withThrowable(Throwable throwable) {
        this.args = new Object[] { throwable };
        return this;
    }

    public LogEventSampler withLoggerName(String loggerName) {
        this.loggerName = Optional.ofNullable(loggerName);
        return this;
    }

    public LogEventSampler withFormat(String format) {
        this.format = Optional.of(format);
        return this;
    }

    public LogEventSampler withArgs(Object... args) {
        this.args = args;
        return this;
    }

    public LogEventSampler withLevel(Level level) {
        this.level = Optional.of(level);
        return this;
    }

    public LogEventSampler withRandomTime() {
        return this.withTime(Instant.now().minusSeconds(random.nextInt(3600) + 60));
    }

}
