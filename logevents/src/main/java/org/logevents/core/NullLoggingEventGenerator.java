package org.logevents.core;

import org.logevents.LogEventObserver;
import org.slf4j.Marker;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.NOPLoggingEventBuilder;

/**
 * Log generator which does nothing. Saves if-checks when logging to levels that have been turned off
 */
public class NullLoggingEventGenerator implements LogEventGenerator {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public LoggingEventBuilder atLevel() {
        return NOPLoggingEventBuilder.singleton();
    }

    @Override
    public boolean isEnabled(Marker marker) {
        return false;
    }

    @Override
    public void log(String msg) {
    }

    @Override
    public void log(String format, Object arg) {
    }

    @Override
    public void log(String format, Throwable t) {
    }

    @Override
    public void log(String format, Object arg1, Object arg2) {
    }

    @Override
    public void log(String format, Object... args) {
    }

    @Override
    public void log(Marker marker, String msg) {
    }

    @Override
    public void log(Marker marker, String format, Object arg) {
    }

    @Override
    public void log(Marker marker, String format, Object arg1, Object arg2) {
    }

    @Override
    public void log(Marker marker, String format, Object... args) {
    }

    @Override
    public LogEventObserver getObservers() {
        return new NullLogEventObserver();
    }

    NullLoggingEventGenerator() {}
}
