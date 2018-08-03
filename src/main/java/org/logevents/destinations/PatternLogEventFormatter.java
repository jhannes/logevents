package org.logevents.destinations;

import static org.logevents.destinations.LogEventFormatter.restrictLength;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
        String format(LogEvent event, Optional<Integer> padding, Optional<Integer> maxLength, Optional<LogEventFormatter> subpattern, List<String> parameters);
    }

    private static Map<String, LogEventFormatter> simpleConverters = new HashMap<>();
    private static Map<String, FormattingFunction> functionalConverters = new HashMap<>();

    static {
        simpleConverters.put("logger", e -> e.getLoggerName());
        simpleConverters.put("level", e -> e.getLevel().toString());
        functionalConverters.put("message",
                (event, padding, maxLen, subpattern, parameters) -> restrictLength(event.formatMessage(), padding, maxLen));
        functionalConverters.put("date",
                (event, padding, maxLen, subpattern, parameters) -> {
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
                    if (!parameters.isEmpty()) {
                        formatter = DateTimeFormatter.ofPattern(parameters.get(0));
                    }
                    ZoneId zone = ZoneId.systemDefault();
                    if (parameters.size() >= 2) {
                        zone = ZoneId.of(parameters.get(1));
                    }
                    ZonedDateTime time = event.getInstant().atZone(zone);
                    return restrictLength(formatter.format(time), padding, maxLen);
                });
        functionalConverters.put("cyan",
                (event, padding, maxLen, subpattern, parameters) -> {
                    return restrictLength(
                            "\033[36m" + subpattern.map(p -> p.format(event)).orElse("") + "\033[m",
                            padding, maxLen);
                });

    }


    private String pattern;
    private LogEventFormatter converter;
    private StringScanner scanner;

    @Override
    public String format(LogEvent event) {
        return converter.format(event);
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
        this.scanner = new StringScanner(pattern, 0);
        this.converter = readConverter(scanner, '\0');
    }

    private LogEventFormatter readConverter(StringScanner scanner, char terminator) {
        List<LogEventFormatter> converters = new ArrayList<>();
        while (scanner.hasMoreCharacters()) {
            readConstant(scanner, converters);
            readConverter(scanner, converters);
        }
        return event -> converters.stream()
                .map(converter -> converter.format(event))
                .collect(Collectors.joining(""));
    }

    private void readConstant(StringScanner scanner, List<LogEventFormatter> converters) {
        if (scanner.hasMoreCharacters()) {
            converters.add(getConstant(scanner.readUntil('%')));
        }
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
    private void readConverter(StringScanner scanner, List<LogEventFormatter> converters) {
        PatternConverterBuilder builder = new PatternConverterBuilder(scanner);
        builder.readConversion(this).ifPresent(c -> converters.add(c));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + pattern + "}";
    }

}
