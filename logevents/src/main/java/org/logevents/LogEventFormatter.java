package org.logevents;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.config.MdcFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a strategy for converting a LogEvent to a string.
 *
 * @author Johannes Brodwall
 *
 */
public interface LogEventFormatter extends Function<LogEvent, String> {

    @Override
    String apply(LogEvent logEvent);

    default Optional<ExceptionFormatter> getExceptionFormatter() {
        return Optional.empty();
    }

    default void configure(Configuration configuration) {
    }
}
