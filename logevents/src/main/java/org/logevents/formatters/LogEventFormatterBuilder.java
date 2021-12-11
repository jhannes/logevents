package org.logevents.formatters;

import org.logevents.LogEventFormatter;
import org.logevents.formatters.pattern.PatternConverterSpecWithSubpattern;

@FunctionalInterface
public interface LogEventFormatterBuilder {
    LogEventFormatter apply(PatternConverterSpecWithSubpattern<LogEventFormatter> spec);
}
