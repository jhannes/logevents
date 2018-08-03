package org.logevents.destinations;

import static org.logevents.destinations.LogEventFormatter.restrictLength;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.logevents.LogEvent;
import org.logevents.util.Configuration;

/**
 * This class represents a {@link LogEventFormatter} which outputs the
 * {@link LogEvent} based on a configured pattern. The pattern consists of
 * constant parts and conversion parts, which starts with %-signs. Conversion
 * parts are handled with {@link PatternConverterSpec}. A conversion
 * is specified with a conversion word, and you can extend {@link PatternLogEventFormatter}
 * with your own conversion words by ....
 *
 * @author Johannes Brodwall
 *
 */
public class PatternLogEventFormatter implements LogEventFormatter {

    static class ConverterBuilderFactory {
        private static Map<String, Function<PatternConverterSpec, LogEventFormatter>> converterBuilders = new HashMap<>();

        public void putWithoutLengthRestriction(String conversionWord, Function<PatternConverterSpec, LogEventFormatter> converterBuilder) {
            converterBuilders.put(conversionWord, converterBuilder);
        }

        public void put(String conversionWord, Function<PatternConverterSpec, LogEventFormatter> converterBuilder) {
            converterBuilders.put(conversionWord,
                    spec -> {
                        LogEventFormatter f = converterBuilder.apply(spec);
                        Optional<Integer> minLength = spec.getMinLength(),
                                maxLength = spec.getMaxLength();
                        return e -> restrictLength(f.format(e), minLength, maxLength);
                    });
        }

        public Function<PatternConverterSpec, LogEventFormatter> get(String conversionWord) {
            return converterBuilders.get(conversionWord);
        }
    }

    private static ConverterBuilderFactory factory = new ConverterBuilderFactory();

    static {
        factory.put("logger",spec -> e -> e.getLoggerName());
        factory.put("level", spec -> e -> e.getLevel().toString());
        factory.put("message", spec -> e -> e.formatMessage());

        factory.put("date", spec -> {
            DateTimeFormatter formatter = spec.getParameter(0)
                    .map(DateTimeFormatter::ofPattern)
                    .orElse(DateTimeFormatter.ISO_INSTANT);
            ZoneId zone = spec.getParameter(1)
                    .map(ZoneId::of)
                    .orElse(ZoneId.systemDefault());
            return e -> formatter.format(e.getInstant().atZone(zone));
        });

        factory.put("replace", spec -> {
            Optional<LogEventFormatter> subpattern = spec.getSubpattern();
            String regex = spec.getParameters().get(0);
            String replacement = spec.getParameters().get(1);
            return e -> subpattern.map(p -> p.format(e)).orElse("").replaceAll(regex, replacement);
        });

        factory.put("cyan", spec -> e -> "\033[36m" + spec.getSubpattern().map(p -> p.format(e)).orElse("") + "\033[m");
        factory.put("red", spec -> e -> "\033[41m" + spec.getSubpattern().map(p -> p.format(e)).orElse("") + "\033[m");
    }


    private String pattern;
    private LogEventFormatter converter;
    private StringScanner scanner;

    private LogEventFormatter createConverter(PatternConverterSpec builder) {
        Function<PatternConverterSpec, LogEventFormatter> function = factory.get(builder.getConversionWord());
        if (function != null) {
            return function.apply(builder);
        }
        throw new IllegalArgumentException("Unknown conversion " + builder.getConversionWord());
    }

    public PatternLogEventFormatter(String pattern) {
        setPattern(pattern);
    }

    public PatternLogEventFormatter(Properties properties, String prefix) {
        this(new Configuration(properties, prefix).getString("pattern"));
    }

    LogEventFormatter readConverter(StringScanner scanner, char terminator) {
        List<LogEventFormatter> converters = new ArrayList<>();
        while (scanner.hasMoreCharacters() && scanner.current() != terminator) {
            readConstant(scanner, converters, terminator);
            if (scanner.hasMoreCharacters() && scanner.current() != terminator) {
                readConverter(scanner, converters);
            }
        }
        return event -> converters.stream()
                .map(converter -> converter.format(event))
                .collect(Collectors.joining(""));
    }

    private void readConstant(StringScanner scanner, List<LogEventFormatter> converters, char terminator) {
        StringBuilder text = new StringBuilder();
        while (scanner.hasMoreCharacters()) {
            if (scanner.current() == terminator || scanner.current() == '%') {
                break;
            }
            text.append(scanner.advance());
        }
        if (text.length() > 0) {
            converters.add(getConstant(text.toString()));
        }
    }

    private void readConverter(StringScanner scanner, List<LogEventFormatter> converters) {
        PatternConverterSpec builder = new PatternConverterSpec(scanner);
        builder.readConversion(this);
        converters.add(createConverter(builder));
    }

    @Override
    public String format(LogEvent event) {
        return converter.format(event);
    }

    private static LogEventFormatter getConstant(String string) {
        return e -> string;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
        this.scanner = new StringScanner(pattern, 0);
        this.converter = readConverter(scanner, '\0');
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + pattern + "}";
    }

}
