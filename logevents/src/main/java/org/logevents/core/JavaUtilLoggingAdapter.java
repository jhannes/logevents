package org.logevents.core;

import org.logevents.LogEventFactory;
import org.slf4j.spi.LocationAwareLogger;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Stream;

/**
 * An adapter from java.util.logging to LogEvents. Automatically installed by {@link LogEventFactory}
 */
public class JavaUtilLoggingAdapter extends Handler {
    private final LoggerDelegator loggerDelegator;

    // Ensure that the weak reference is not finalized
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final Logger julLogger;
    private final SimpleFormatter simpleFormatter = new SimpleFormatter();

    public JavaUtilLoggingAdapter(LoggerDelegator loggerDelegator, Logger julLogger) {
        this.loggerDelegator = loggerDelegator;
        this.julLogger = julLogger;
        julLogger.setLevel(toJavaUtilLoggingLevel(loggerDelegator.getEffectiveFilter().getThreshold()));
        julLogger.setUseParentHandlers(false);
    }

    /**
     * Ensures that logging to {@link java.util.logging.Logger} is intercepted. Removes existing java.util.logging
     * handlers and adds a new {@link JavaUtilLoggingAdapter}
     */
    public static void installHandler(LoggerDelegator logger) {
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(getName(logger));
        Stream.of(julLogger.getHandlers()).forEach(julLogger::removeHandler);
        julLogger.addHandler(new JavaUtilLoggingAdapter(logger, julLogger));
    }

    private static String getName(LoggerDelegator logger) {
        return logger.getName().equals("ROOT") ? "" : logger.getName();
    }

    private static Level toJavaUtilLoggingLevel(org.slf4j.event.Level level) {
        if (level==null) {
            return Level.OFF;
        }
        switch (level) {
            case ERROR: return Level.SEVERE;
            case WARN: return Level.WARNING;
            case INFO: return Level.INFO;
            case DEBUG: return Level.FINE;
            case TRACE: return Level.ALL;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void publish(LogRecord record) {
        if (record != null) {
            LogEventGenerator generator = getLogEventGenerator(record);
            if (generator.isEnabled()) {
                if (record.getThrown() != null) {
                    generator.log(simpleFormatter.formatMessage(record), record.getThrown());
                } else {
                    generator.log(simpleFormatter.formatMessage(record));
                }
            }
        }
    }

    private LogEventGenerator getLogEventGenerator(LogRecord record) {
        org.slf4j.event.Level level = LoggerDelegator.getLevel(fromJavaUtilLoggingLevel(record.getLevel()));
        return loggerDelegator.getLogger(level);
    }

    private int fromJavaUtilLoggingLevel(Level level) {
        if (level.intValue() <= Level.FINER.intValue()) {
            return LocationAwareLogger.TRACE_INT;
        } else if (level.intValue() <= Level.FINE.intValue()) {
            return LocationAwareLogger.DEBUG_INT;
        } else if (level.intValue() <= Level.INFO.intValue()) {
            return LocationAwareLogger.INFO_INT;
        } else if (level.intValue() <= Level.WARNING.intValue()) {
            return LocationAwareLogger.WARN_INT;
        } else {
            return LocationAwareLogger.ERROR_INT;
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() throws SecurityException {

    }
}
