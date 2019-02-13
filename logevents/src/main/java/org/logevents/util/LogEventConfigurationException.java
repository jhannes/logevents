package org.logevents.util;

public class LogEventConfigurationException extends RuntimeException {

    public LogEventConfigurationException(String message) {
        super(message);
    }

    public LogEventConfigurationException(String message, Throwable throwable) {
        super(message + ": " + throwable, throwable);
    }

}
