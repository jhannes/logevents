package org.logevents;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
    private String threadName = Thread.currentThread().getName();
    private long timestamp = System.currentTimeMillis();
    private Throwable throwable;

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

}
