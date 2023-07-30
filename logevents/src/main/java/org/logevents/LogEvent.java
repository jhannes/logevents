package org.logevents;

import org.logevents.config.MdcFilter;
import org.logevents.core.JavaUtilLoggingAdapter;
import org.logevents.core.LoggerDelegator;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.mdc.DynamicMDC;
import org.logevents.optional.junit.LogEventSampler;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The representation of a log event. This class is passed to all
 * {@link LogEventObserver} instances and used internally.
 * <p>
 * When using LogEvent, be aware that {@link #getCallerLocation()} is initialized lazily.
 * This will fail it's not accessed the first time inside a call to {@link LogEventObserver}
 * (for example, if accessed in another thread or after {@link LogEventObserver#logEvent(LogEvent)}
 * returned).
 *
 * @author Johannes Brodwall
 */
public class LogEvent implements LoggingEvent {

    private final String loggerName;
    private final Level level;
    private final Marker marker;
    private final String messageFormat;
    private final Object[] args;
    private final Throwable throwable;
    private final long threadId = Thread.currentThread().getId();
    private final String threadName;
    private final long timestamp;
    private final Map<String, String> mdcProperties;

    private final Map<String, DynamicMDC> dynamicMdcProperties;

    private StackTraceElement callerLocation;
    private StackTraceElement[] stackTrace;
    private final List<KeyValuePair> keyValuePairs = new ArrayList<>();

    public LogEvent(
            String loggerName,
            Level level,
            Marker marker,
            String messageFormat,
            Object[] args,
            String threadName,
            Instant timestamp,
            Map<String, String> mdcProperties,
            Map<String, DynamicMDC> dynamicMdcProperties
    ) {
        this.loggerName = loggerName;
        this.level = level;
        this.marker = marker;
        this.messageFormat = messageFormat;
        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            this.args = new Object[args.length - 1];
            System.arraycopy(args, 0, this.args, 0, this.args.length);
            this.throwable = (Throwable) args[args.length - 1];
        } else {
            this.args = args;
            this.throwable = null;
        }
        this.threadName = threadName;
        this.timestamp = timestamp.toEpochMilli();
        this.mdcProperties = mdcProperties == null ? Collections.emptyMap() : mdcProperties;
        this.dynamicMdcProperties = dynamicMdcProperties == null ? Collections.emptyMap() : dynamicMdcProperties;
    }

    public LogEvent(String loggerName, Level level, Marker marker, String messageFormat, Object[] args) {
        this(
                loggerName,
                level,
                marker,
                messageFormat,
                args,
                Thread.currentThread().getName(),
                Instant.now(),
                DynamicMDC.getCopyOfStaticContext(),
                DynamicMDC.getCopyOfDynamicContext()
        );
    }

    public String getMdcString(MdcFilter mdcFilter) {
        List<String> mdcValue = new ArrayList<>();
        getMdcProperties().keySet()
                .stream().filter(mdcFilter::isKeyIncluded)
                .forEach(key -> mdcValue.add(key + "=" + getMdcProperties().get(key)));
        return mdcValue.isEmpty() ? "" : " {" + String.join(", ", mdcValue) + "}";
    }

    /**
     * Message Diagnostics Context set with {@link MDC}. Copied when
     * {@link LogEvent} is created and can be safely used after {@link LogEventObserver#logEvent(LogEvent)}
     * returns.
     */
    public Map<String, String> getMdcProperties() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(mdcProperties);
        DynamicMDC.collect(result, dynamicMdcProperties);
        return result;
    }

    public Map<String, String> getStaticMdcProperties() {
        return mdcProperties;
    }

    public Map<String, DynamicMDC> getDynamicMdcProperties() {
        return dynamicMdcProperties;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    public Marker getMarker() {
        return marker;
    }

    @Override
    public List<Marker> getMarkers() {
        return Collections.singletonList(marker);
    }

    @Override
    public String getLoggerName() {
        return loggerName;
    }

    public String getLoggerName(Optional<Integer> length) {
        if (length.isPresent()) {
            return getAbbreviatedLoggerName(length.get());
        }
        return getLoggerName();
    }


    /**
     * Returns the logger name restricted as much as possible to fit maxLength characters.
     * The final component of the name is prioritized, then each part from the beginning.
     * For example "org.example.Logger" will be abbreviated to "o.e.Logger" or "org.e.Logger".
     */
    public String getAbbreviatedLoggerName(int maxLength) {
        return getAbbreviatedClassName(loggerName, maxLength);
    }

    /**
     * Returns the logger name restricted as much as possible to fit maxLength characters.
     * The final component of the name is prioritized, then each part from the beginning.
     * For example "org.example.Logger" will be abbreviated to "o.e.Logger" or "org.e.Logger".
     */
    public static String getAbbreviatedClassName(String className, int maxLength) {
        String[] parts = className.split("\\.");
        String lastPartName = parts[parts.length - 1];
        int remainder = maxLength - lastPartName.length() - ((parts.length - 1) * 2);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].length() > remainder) {
                remainder = 0;
                result.append(parts[i].charAt(0)).append(".");
            } else {
                remainder -= parts[i].length() + 1;
                result.append(parts[i]).append(".");
            }
        }
        result.append(lastPartName);
        return result.toString();
    }

    @Override
    public String getMessage() {
        return messageFormat;
    }

    public String getMessage(MessageFormatter messageFormatter) {
        return messageFormatter.format(messageFormat, args);
    }

    @Override
    public String getThreadName() {
        return threadName;
    }

    @Override
    public List<Object> getArguments() {
        return Arrays.asList(args);
    }

    @Override
    public Object[] getArgumentArray() {
        return args;
    }

    @Override
    public long getTimeStamp() {
        return timestamp;
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public List<KeyValuePair> getKeyValuePairs() {
        return keyValuePairs;
    }

    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    public ZonedDateTime getZonedDateTime() {
        return getInstant().atZone(ZoneId.systemDefault());
    }

    public LocalTime getLocalTime() {
        return getZonedDateTime().toLocalTime();
    }

    public Throwable getRootThrowable() {
        Throwable throwable = this.throwable;
        if (throwable != null) {
            while (throwable.getCause() != null && throwable.getCause() != throwable) {
                throwable = throwable.getCause();
            }
        }
        return throwable;
    }

    /**
     * The StackTraceElement from which a method on {@link Logger} was first called
     * to cause this logging event. Is initialized lazily and must be called first time
     * during the log observation.
     */
    public StackTraceElement getCallerLocation() {
        if (callerLocation != null) {
            return callerLocation;
        }
        if (this.threadId != Thread.currentThread().getId()) {
            throw new IllegalStateException("Can't find caller location from different thread");
        }
        this.callerLocation = extractCallerLocation(getStackTrace());
        return callerLocation;
    }

    void setCallerLocation(StackTraceElement callerLocation) {
        this.callerLocation = callerLocation;
    }

    /**
     * Returns the first non-logger element of the argument stackTrace.
     * Will lock for a LogEvents entrypoint and then scan until first class
     * that's not in a known logging package
     */
    StackTraceElement extractCallerLocation(StackTraceElement[] stackTrace) {
        for (int i = 0; i < stackTrace.length - 1; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            if (stackTraceElement.getClassName().equals(LoggerDelegator.class.getName())) {
                assert !stackTrace[i + 1].getClassName().startsWith("org.slf4j.");

                while (isLoggingClass(stackTrace[i + 1])) {
                    i++;
                }

                return stackTrace[i + 1];
            } else if (stackTraceElement.getClassName().equals("org.logevents.core.LogEventBuilder")) {
                return stackTrace[i + 1];
            } else if (stackTraceElement.getClassName().equals(LogEventSampler.class.getName())) {
                return stackTrace[i + 1];
            } else if (stackTraceElement.getClassName().equals(JavaUtilLoggingAdapter.class.getName())) {
                while (isLoggingClass(stackTrace[i + 1])) {
                    i++;
                }
                return stackTrace[i + 1];
            }
        }
        throw new RuntimeException("Could not find calling stack trace element!");
    }

    /**
     * Returns the logging location in a format that is usually clickable in IntelliJ, e.g.
     * LogEvent.getSimpleCallerLocation(LogEvent.java:259)
     */
    public String getSimpleCallerLocation() {
        StackTraceElement callerLocation = getCallerLocation();
        String className = callerLocation.getClassName();
        className = className.substring(className.lastIndexOf(".") + 1);
        return className + "." + callerLocation.getMethodName()
               + "(" + callerLocation.getFileName() + ":" + callerLocation.getLineNumber() + ")";
    }

    private boolean isLoggingClass(StackTraceElement stackTraceElement) {
        String className = stackTraceElement.getClassName();
        return className.startsWith("org.logevents.")
               || className.startsWith("sun.reflect.")
               || className.startsWith("jdk.internal.reflect.")
               || className.startsWith("java.lang.reflect.")
               || className.startsWith("org.apache.commons.logging.")
               || className.startsWith("org.flywaydb.core.internal.util.logging.")
               || className.startsWith("org.flywaydb.core.internal.logging.")
               || className.startsWith("org.eclipse.jetty.util.log")
               || className.startsWith("java.util.logging.")
               || className.startsWith("sun.util.logging.")
               || className.startsWith("sun.rmi.runtime.Log")
               || className.startsWith("sun.security.ssl.SSLLogger")
               || className.startsWith("org.log4j.");
    }

    public StackTraceElement[] getStackTrace() {
        if (stackTrace == null) {
            stackTrace = new Throwable().getStackTrace();
        }
        return stackTrace;
    }

    public int getCallerLine() {
        return getCallerLocation().getLineNumber();
    }

    public String getCallerMethodName() {
        return getCallerLocation().getMethodName();
    }

    public String getCallerClassName() {
        return getCallerLocation().getClassName();
    }

    public String getCallerFileName() {
        return getCallerLocation().getFileName();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + loggerName + "," + level + "," + messageFormat + "}";
    }

    public String getMdc() {
        return this.getMdcProperties()
                .entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    public String getKeyValuePairsString() {
        return getKeyValuePairs().stream().map(KeyValuePair::toString).collect(Collectors.joining(" "));
    }

    public String getMdc(String key, String defaultValue) {
        return this.getMdcProperties().getOrDefault(key, defaultValue);
    }

    public int compareTo(LogEvent other) {
        int compared = getLevel().compareTo(other.getLevel());
        return compared != 0 ? -compared : -getInstant().compareTo(other.getInstant());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEvent logEvent = (LogEvent) o;
        return threadId == logEvent.threadId &&
               timestamp == logEvent.timestamp &&
               Objects.equals(loggerName, logEvent.loggerName) &&
               level == logEvent.level &&
               Objects.equals(marker, logEvent.marker) &&
               Objects.equals(messageFormat, logEvent.messageFormat) &&
               Objects.equals(threadName, logEvent.threadName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loggerName, level, marker, messageFormat, threadId, threadName, timestamp);
    }

    public boolean isBelowThreshold(Level threshold) {
        return getLevel().compareTo(threshold) > 0;
    }

    public void populateJson(Map<String, Object> json) {
        for (DynamicMDC dynamicMDC : dynamicMdcProperties.values()) {
            dynamicMDC.populateJsonEvent(json, MdcFilter.INCLUDE_ALL, null);
        }
    }
}
