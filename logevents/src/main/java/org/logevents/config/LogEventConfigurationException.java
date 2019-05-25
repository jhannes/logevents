package org.logevents.config;

public class LogEventConfigurationException extends RuntimeException {

    public LogEventConfigurationException(String message) {
        super(message);
    }

    public LogEventConfigurationException(String message, Throwable throwable) {
        super(message + ": " + throwable, throwable);
    }

}
