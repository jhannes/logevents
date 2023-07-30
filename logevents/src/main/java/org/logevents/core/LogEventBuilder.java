package org.logevents.core;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

class LogEventBuilder implements LoggingEventBuilder {
    private final String loggerName;
    private final Level level;
    private final LogEventObserver observer;
    private String message;
    private final List<Object> arguments = new ArrayList<>();
    private Marker marker;
    private final List<KeyValuePair> keyValuePairs = new ArrayList<>();

    public LogEventBuilder(String loggerName, Level level, LogEventObserver observer) {
        this.loggerName = loggerName;
        this.level = level;
        this.observer = observer;
    }

    @Override
    public LoggingEventBuilder setCause(Throwable cause) {
        return this;
    }

    @Override
    public LoggingEventBuilder addMarker(Marker marker) {
        this.marker = marker;
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(Object p) {
        arguments.add(p);
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(Supplier<?> objectSupplier) {
        return addArgument(objectSupplier.get());
    }

    @Override
    public LoggingEventBuilder addKeyValue(String key, Object value) {
        keyValuePairs.add(new KeyValuePair(key, value));
        return this;
    }

    @Override
    public LoggingEventBuilder addKeyValue(String key, Supplier<Object> valueSupplier) {
        return addKeyValue(key, valueSupplier.get());
    }

    @Override
    public LoggingEventBuilder setMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    public LoggingEventBuilder setMessage(Supplier<String> messageSupplier) {
        return setMessage(messageSupplier.get());
    }

    @Override
    public void log() {
        LogEvent event = new LogEvent(this.loggerName, this.level, marker, message, arguments.toArray());
        event.getKeyValuePairs().addAll(keyValuePairs);
        observer.logEvent(event);
    }

    @Override
    public void log(String message) {
        setMessage(message);
        log();
    }

    @Override
    public void log(String message, Object arg) {
        addArgument(arg);
        log(message);
    }

    @Override
    public void log(String message, Object arg0, Object arg1) {
        addArgument(arg0);
        addArgument(arg1);
        log(message);
    }

    @Override
    public void log(String message, Object... args) {
        arguments.addAll(Arrays.asList(args));
        log(message);
    }

    @Override
    public void log(Supplier<String> messageSupplier) {
        log(messageSupplier.get());
    }
}
