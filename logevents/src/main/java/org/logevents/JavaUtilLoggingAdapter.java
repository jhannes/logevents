package org.logevents;

import org.slf4j.spi.LocationAwareLogger;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

public class JavaUtilLoggingAdapter extends Handler {
    private LogEventFactory factory;

    public JavaUtilLoggingAdapter(LogEventFactory factory) {
        this.factory = factory;
    }

    /**
     * Ensures that logging to {@link java.util.logging.Logger} is intercepted.
     */
    public static void install(LogEventFactory factory) {
        java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
        Stream.of(rootLogger.getHandlers()).forEach(rootLogger::removeHandler);
        rootLogger.addHandler(new JavaUtilLoggingAdapter(factory));
    }

    @Override
    public void publish(LogRecord record) {
        if (record != null) {
            factory.getLogger(record.getLoggerName()).log(
                    null,
                    java.util.logging.Logger.class.getName(),
                    fromJavaUtilLoggingLevel(record.getLevel()),
                    record.getMessage(), // May be null
                    record.getParameters(),
                    record.getThrown()
            );
        }
    }

    private int fromJavaUtilLoggingLevel(Level level) {
        if (level.intValue() <= Level.FINER.intValue()) {
            return LocationAwareLogger.TRACE_INT;
        } else if (level.intValue() <= Level.FINEST.intValue()) {
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
