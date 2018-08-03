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
import java.util.Properties;
import java.util.stream.Collectors;

import org.logevents.LogEvent;
import org.logevents.util.Configuration;

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
        functionalConverters.put("replace",
                (event, minLen, maxLen, subpatter, parameters) -> {
                    String msg = subpatter.map(m -> m.format(event)).orElse("");
                    msg = msg.replaceAll(parameters.get(0), parameters.get(1));
                    return restrictLength(msg, minLen, maxLen);
                });
        functionalConverters.put("message",
                (event, minLen, maxLen, subpattern, parameters) -> restrictLength(event.formatMessage(), minLen, maxLen));
        functionalConverters.put("date",
                (event, minLen, maxLen, subpattern, parameters) -> {
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
                    if (!parameters.isEmpty()) {
                        formatter = DateTimeFormatter.ofPattern(parameters.get(0));
                    }
                    ZoneId zone = ZoneId.systemDefault();
                    if (parameters.size() >= 2) {
                        zone = ZoneId.of(parameters.get(1));
                    }
                    ZonedDateTime time = event.getInstant().atZone(zone);
                    return restrictLength(formatter.format(time), minLen, maxLen);
                });
        functionalConverters.put("cyan",
                (event, minLen, maxLen, subpattern, parameters) -> {
                    return restrictLength(
                            "\033[36m" + subpattern.map(p -> p.format(event)).orElse("") + "\033[m",
                            minLen, maxLen);
                });
        functionalConverters.put("red",
                (event, minLen, maxLen, subpattern, parameters) -> {
                    return restrictLength(
                            "\033[41m" + subpattern.map(p -> p.format(event)).orElse("") + "\033[m",
                            minLen, maxLen);
                });

    }


    private String pattern;
    private LogEventFormatter converter;
    private StringScanner scanner;

    public PatternLogEventFormatter(String pattern) {
        setPattern(pattern);
    }

    public PatternLogEventFormatter(Properties properties, String prefix) {
        this(new Configuration(properties, prefix).getString("pattern"));
    }

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

    LogEventFormatter readConverter(StringScanner scanner, char terminator) {
        List<LogEventFormatter> converters = new ArrayList<>();
        while (scanner.hasMoreCharacters() && scanner.current() != terminator) {
            readConstant(scanner, converters, terminator);
            if (scanner.current() != terminator) {
                readConverter(scanner, converters);
            }
        }
        return event -> converters.stream()
                .map(converter -> converter.format(event))
                .collect(Collectors.joining(""));
    }

    private void readConstant(StringScanner scanner, List<LogEventFormatter> converters, char terminator) {
        if (scanner.hasMoreCharacters()) {
            StringBuilder text = new StringBuilder();
            while (scanner.hasMoreCharacters()) {
                if (scanner.current() == terminator || scanner.current() == '%') {
                    break;
                }
                text.append(scanner.advance());
            }
            converters.add(getConstant(text.toString()));
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
