package org.logevents;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.MessageFormatter;

/**
 * The representation of a log event. This class is passed to all
 * {@link LogEventObserver} instances and used internally. The most the most
 * used method is {@link #formatMessage()}, which formats {@link #getMessage()}
 * with {@link #getArgumentArray()}.
 * <p>
 * When using LogEvent, be aware that {@link #getCallerLocation()} is initialized lazily.
 * This will fail it's not accessed the first time inside a call to {@link LogEventObserver}
 * (for example, if accessed in another thread or after {@link LogEventObserver#logEvent(LogEvent)}
 * returned.
 *
 * @author Johannes Brodwall
 *
 */
public class LogEvent implements LoggingEvent {

    private final String loggerName;
    private final Level level;
    private final Marker marker;
    private final String format;
    private final Object[] args;
    private Throwable throwable;
    private final long threadId = Thread.currentThread().getId();
    private final String threadName = Thread.currentThread().getName();
    private final long timestamp;
    private final Map<String, String> mdcProperties;

    private StackTraceElement callerLocation;
    private StackTraceElement[] stackTrace;

    public LogEvent(String loggerName, Level level, Instant timestamp, Marker marker, String format, Object... args) {
        this.loggerName = loggerName;
        this.level = level;
        this.marker = marker;
        this.format = format;
        if (args.length > 0 && args[args.length-1] instanceof Throwable) {
            this.args = new Object[args.length-1];
            System.arraycopy(args, 0, this.args, 0, this.args.length);
            this.throwable = (Throwable) args[args.length-1];
        } else {
            this.args = args;
        }
        this.timestamp = timestamp.toEpochMilli();

        this.mdcProperties = Optional.ofNullable(MDC.getCopyOfContextMap()).orElse(new HashMap<>());
    }

    public LogEvent(String loggerName, Level level, Marker marker, String format, Object... args) {
        this(loggerName, level, Instant.now(), marker, format, args);
    }

    public LogEvent(String loggerName, Level level, String format, Object... args) {
        this(loggerName, level, Instant.now(), null, format, args);
    }

    /**
     * Message Diagnostics Context set with {@link MDC}. Copied when
     * {@link LogEvent} is created and can be safely used after {@link LogEventObserver#logEvent(LogEvent)}
     * returns.
     */
    public Map<String, String> getMdcProperties() {
        return mdcProperties;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public Marker getMarker() {
        return marker;
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
        String[] parts = loggerName.split("\\.");
        String lastPartName = parts[parts.length-1];
        int remainder = maxLength - lastPartName.length() - ((parts.length-1) * 2);

        StringBuilder result = new StringBuilder();
        for (int i=0; i<parts.length-1; i++) {
            if (parts[i].length() > remainder) {
                remainder = 0;
                result.append(parts[i].charAt(0)).append(".");
            } else {
                remainder -= parts[i].length()+1;
                result.append(parts[i]).append(".");
            }
        }
        result.append(lastPartName);
        return result.toString();
    }


    @Override
    public String getMessage() {
        return format;
    }

    @Override
    public String getThreadName() {
        return threadName;
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

    public String formatMessage() {
        return MessageFormatter.arrayFormat(format, args).getMessage();
    }

    public ZonedDateTime getZonedDateTime() {
        return getInstant().atZone(ZoneId.systemDefault());
    }

    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
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
        StackTraceElement[] stackTrace = getStackTrace();
        for (int i = 0; i < stackTrace.length-1; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            if (stackTraceElement.getClassName().equals(LoggerDelegator.class.getName())) {
                assert !stackTrace[i+1].getClassName().startsWith("org.slf4j.");

                while (isLoggingClass(stackTrace[i+1])) {
                    i++;
                }

                this.callerLocation = stackTrace[i+1];
                return callerLocation;
            }
        }
        throw new RuntimeException("Could not find calling stack trace element!");
    }

    private boolean isLoggingClass(StackTraceElement stackTraceElement) {
        String className = stackTraceElement.getClassName();
        return className.startsWith("org.apache.commons.logging.") || className.startsWith("org.flywaydb.core.internal.util.logging.")
                || className.startsWith("java.util.logging.") || className.startsWith("org.log4j.");
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
        return getClass().getSimpleName() + "{" + level + "," + format + "}";
    }

    public String getMdc() {
        return this.getMdcProperties()
            .entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
    }

    public String getMdc(String key, String defaultValue) {
        return this.getMdcProperties().getOrDefault(key, defaultValue);
    }


}
