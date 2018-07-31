package org.logevents;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.MessageFormatter;

public class LogEvent implements LoggingEvent {

    private String loggerName;
    private Level level;
    private Marker marker;
    private String format;
    private Object[] args;
    private long threadId = Thread.currentThread().getId();
    private String threadName = Thread.currentThread().getName();
    private long timestamp = System.currentTimeMillis();
    private Throwable throwable;
    private Map<String, String> mdcProperties;

    private StackTraceElement callerLocation;

    public LogEvent(String loggerName, Level level, Marker marker, String format, Object[] args) {
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

        this.mdcProperties = Optional.ofNullable(MDC.getCopyOfContextMap()).orElse(new HashMap<>());
    }

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

    public String getLoggerName(int maxLength) {
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
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault());
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

    public StackTraceElement getCallerLocation() {
        if (callerLocation != null) {
            return callerLocation;
        }
        if (this.threadId != Thread.currentThread().getId()) {
            throw new IllegalStateException("Can't find called from different thread");
        }
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (int i = 0; i < stackTrace.length-1; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            if (stackTraceElement.getClassName().equals(LoggerDelegator.class.getName())) {
                assert !stackTrace[i+1].getClassName().startsWith("org.logevents.");
                assert !stackTrace[i+1].getClassName().startsWith("org.slf4j.");
                this.callerLocation = stackTrace[i+1];
                return callerLocation;
            }
        }
        throw new RuntimeException("Could not find calling stack trace element!");
    }

    public String formatStackTrace() {
        if (getThrowable() != null) {
            StringWriter s = new StringWriter();
            getThrowable().printStackTrace(new PrintWriter(s));
            return s.toString();
        }
        return "";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + level + "," + format + "}";
    }


}
