package org.logevents.formatting;

import org.logevents.util.pattern.PatternConverterSpecWithSubpattern;

@FunctionalInterface
public interface LogEventFormatterBuilder {
    LogEventFormatter apply(PatternConverterSpecWithSubpattern<LogEventFormatter> spec);
}
