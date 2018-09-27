package org.logevents.formatting;

import org.logevents.util.pattern.PatternConverterSpec;

@FunctionalInterface
public interface LogEventFormatterBuilder {
    LogEventFormatter apply(PatternConverterSpec<LogEventFormatter> spec);
}