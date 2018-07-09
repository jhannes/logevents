package org.logevents.impl;

import org.slf4j.Marker;

class NullLoggingEventGenerator implements LogEventGenerator {

    @Override
    public boolean isEnabled() {
        return false;
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

}
