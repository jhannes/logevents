package org.logevents.optional.junit;

import org.logevents.LogEvent;
import org.logevents.config.DefaultTestLogEventConfigurator;
import org.logevents.mdc.DynamicMDC;
import org.logevents.mdc.DynamicMDCAdapter;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LogEventSampler {
    public static final Marker HTTP_REQUEST = MarkerFactory.getMarker("HTTP_REQUEST");
    public static final Marker HTTP_ASSET_REQUEST = MarkerFactory.getMarker("HTTP_ASSET_REQUEST");
    public static final Marker HTTP_ERROR = MarkerFactory.getMarker("HTTP_ERROR");
    public static final Marker OPS = MarkerFactory.getMarker("OPS");
    public static final Marker LIFECYCLE = MarkerFactory.getMarker("LIFECYCLE");
    public static final Marker PERFORMANCE = MarkerFactory.getMarker("PERFORMANCE");
    public static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    static {
        HTTP_ASSET_REQUEST.add(HTTP_REQUEST);
        HTTP_ERROR.add(HTTP_REQUEST);
        LIFECYCLE.add(OPS);
    }

    private static final Random random = new Random();

    public static String sampleLoggerName() {
        return pickOne("com", "org", "net")
               + ".example."
               + pickOne("myapp", "app", "superapp")
               + "."
               + pickOne("Customer", "Order", "Person") + pickOne("Controller", "Service", "Repository");
    }

    private Optional<String> threadName = Optional.empty();
    private Optional<String> loggerName = Optional.empty();
    private Optional<Level> level = Optional.empty();
    private Instant timestamp = Instant.now();
    private Marker marker = null;
    private Optional<String> format = Optional.empty();
    private Object[] args = sampleArgs();
    private final Map<String, String> mdc = new LinkedHashMap<>();

    public LogEvent build(DynamicMDCAdapter mdcAdapter) {
        LogEvent logEvent = createEvent(mdcAdapter.getCopyOfStaticContextMap(), mdcAdapter.getCopyOfDynamicContext());
        logEvent.getCallerLocation();
        return logEvent;
    }

    public LogEvent build() {
        Map<String, DynamicMDC> dynamicMdc = DynamicMDC.getCopyOfDynamicContext();
        LogEvent logEvent = createEvent(new HashMap<>(mdc), dynamicMdc);
        logEvent.getCallerLocation();
        return logEvent;
    }

    public LogEvent build(Map<String, String> mdcProperties, Map<String, DynamicMDC> dynamicMdc) {
        LogEvent logEvent = createEvent(mdcProperties, dynamicMdc);
        logEvent.getCallerLocation();
        return logEvent;
    }

    private LogEvent createEvent(Map<String, String> mdcProperties, Map<String, DynamicMDC> dynamicMdc) {
        return new LogEvent(
                loggerName.orElseGet(LogEventSampler::sampleLoggerName),
                level.orElseGet(() -> pickOne(Level.INFO, Level.WARN, Level.ERROR)),
                marker,
                this.format.orElseGet(() -> sampleMessage(args)),
                args,
                threadName.orElseGet(LogEventSampler::sampleThreadName),
                "main",
                timestamp,
                mdcProperties,
                dynamicMdc
        );
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

    @SafeVarargs
    private static <T> T pickOne(T... alternatives) {
        return alternatives[random.nextInt(alternatives.length)];
    }

    private String sampleMessage(Object[] args) {
        int length = args.length;
        if (length > 0 && args[length - 1] instanceof Throwable) length--;
        String ending = IntStream.range(0, length).mapToObj(i -> "{}").collect(Collectors.joining(" "));
        return "Here is a " + level + " test message from " + DefaultTestLogEventConfigurator.getTestMethodName(new Exception().getStackTrace()) + " of " + random.nextInt(10000) + " with " + ending;
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
        this.threadName = Optional.ofNullable(thread);
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

    public LogEventSampler withMdc(Map<String, String> mdc) {
        this.mdc.putAll(mdc);
        return this;
    }

    public LogEventSampler withThrowable(Throwable throwable) {
        this.args = new Object[]{throwable};
        if (!this.level.isPresent()) {
            this.level = Optional.of(Level.WARN);
        }
        return this;
    }

    public LogEventSampler withThrowable() {
        return withThrowable(createThrowable());
    }

    private final StackTraceElement mainMethod = new StackTraceElement("org.logeventsdemo.MyApplication", "main", "MyApplication.java", 20);
    private final StackTraceElement publicMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "publicMethod", "MyClassName.java", 31);
    private final StackTraceElement internalMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "internalMethod", "MyClassName.java", 311);
    private final StackTraceElement nioInternalMethod = new StackTraceElement("sun.nio.fs.WindowsException", "translateToIOException", "WindowsException.java", 79);
    private final StackTraceElement ioApiMethod = new StackTraceElement("java.io.FilterOutputStream", "close", "FilterOutputStream.java", 180);
    private final StackTraceElement ioInternalMethod = new StackTraceElement("java.io.FileOutputStream", "close", "FileOutputStream.java", 323);

    private Throwable createThrowable() {
        IOException exception = new IOException("Something went wrong");
        exception.setStackTrace(new StackTraceElement[]{
                ioInternalMethod, ioApiMethod,
                nioInternalMethod, nioInternalMethod, nioInternalMethod, nioInternalMethod, nioInternalMethod,
                internalMethod, publicMethod, mainMethod
        });
        return exception;
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
        return this.withTime(randomTime());
    }

    public static Instant randomTime() {
        return Instant.now().minusSeconds(random.nextInt(3600) + 60).truncatedTo(ChronoUnit.SECONDS);
    }

}
