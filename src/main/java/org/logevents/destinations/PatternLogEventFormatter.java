package org.logevents.destinations;

import static org.logevents.destinations.LogEventFormatter.restrictLength;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.logevents.LogEvent;

public class PatternLogEventFormatter implements LogEventFormatter {

    @FunctionalInterface
    public static interface FormattingFunction {
        String format(LogEvent event, Optional<Integer> padding, Optional<Integer> maxLength);
    }

    private static Map<String, LogEventFormatter> simpleConverters = new HashMap<>();
    private static Map<String, FormattingFunction> functionalConverters = new HashMap<>();

    static {
        simpleConverters.put("logger", e -> e.getLoggerName());
        simpleConverters.put("level", e -> e.getLevel().toString());

        functionalConverters.put("message",
                (event, padding, maxLen) -> restrictLength(event.formatMessage(), padding, maxLen));
    }


    private String pattern;
    private List<LogEventFormatter> converters;

    @Override
    public String format(LogEvent event) {
        return converters.stream()
            .map(converter -> converter.format(event))
            .collect(Collectors.joining(""));
    }

    static Optional<FormattingFunction> getFormattingFunction(String pattern) {
        return Optional.ofNullable(functionalConverters.get(pattern));
    }

    static Optional<LogEventFormatter> getSimpleFunction(String pattern) {
        return Optional.ofNullable(simpleConverters.get(pattern));
    }

    private static LogEventFormatter getConstant(String string) {
        return e -> string;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;

        List<LogEventFormatter> converters = new ArrayList<>();
        int endIndex = 0;
        while (endIndex < pattern.length()) {
            endIndex = readConstant(pattern, converters, endIndex);
            endIndex = readConverter(pattern, endIndex, converters);
        }
        this.converters = converters;
    }

    private int readConstant(String pattern, List<LogEventFormatter> converters, int startIndex) {
        int endIndex = startIndex;
        if (endIndex < pattern.length()) {
            while (endIndex < pattern.length()) {
                if (pattern.charAt(endIndex) == '%') {
                    break;
                }
                endIndex++;
            }
            converters.add(getConstant(pattern.substring(startIndex, endIndex)));
        }
        return endIndex;
    }

    /**
     * Reads a converter and puts it in the converters list, starting from startIndex.
     * Conversion are on the format
     * "%[&lt;padding&gt;][.&lt;maxlength&gt;]&lt;rule name&gt;[(&lt;conversion pattern&gt;)][{&lt;string&gt;,&lt;string&gt;}]".
     *
     * @param pattern The full string for the pattern formatter
     * @param startIndex The start position for this converter (the position after the %)
     * @param converters The list to insert the new converter into
     * @return position in the pattern to continue reading from
     */
    private int readConverter(String pattern, int startIndex, List<LogEventFormatter> converters) {
        PatternConverterBuilder builder = new PatternConverterBuilder(pattern, startIndex);
        builder.getConverter().ifPresent(c -> converters.add(c));
        return builder.getEndIndex();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + pattern + "}";
    }

}
