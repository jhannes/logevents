package org.logevents.formatting;

import static org.logevents.formatting.LogEventFormatter.restrictLength;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
 * with your own conversion words by adding them to the ConverterBuilderFactory.
 *
 * @author Johannes Brodwall
 *
 */
public class PatternLogEventFormatter implements LogEventFormatter {

    @FunctionalInterface
    public interface ConverterBuilder {
        LogEventFormatter apply(PatternConverterSpec spec);
    }

    static class ConverterBuilderFactory {
        private Map<String, ConverterBuilder> converterBuilders = new HashMap<>();

        public void putWithoutLengthRestriction(String conversionWord, ConverterBuilder converterBuilder) {
            converterBuilders.put(conversionWord, converterBuilder);
        }

        public void putAliases(String conversionWord, String[] aliases) {
            for (String alias : aliases) {
                converterBuilders.put(alias, converterBuilders.get(conversionWord));
            }
        }

        public void put(String conversionWord, ConverterBuilder converterBuilder) {
            putWithoutLengthRestriction(conversionWord,
                    spec -> {
                        LogEventFormatter f = converterBuilder.apply(spec);
                        Optional<Integer> minLength = spec.getMinLength(),
                                maxLength = spec.getMaxLength();
                        return e -> restrictLength(f.format(e), minLength, maxLength);
                    });
        }

        public void putTransformer(String conversionWord, Function<PatternConverterSpec, Function<String, String>> transformerBuilder) {
            put(conversionWord, spec -> {
               LogEventFormatter nestedFormatter = spec.getSubpattern().orElse(e -> "");
               Function<String, String> transformer = transformerBuilder.apply(spec);
               return e -> transformer.apply(nestedFormatter.format(e));
            });
        }

        public ConverterBuilder get(String conversionWord) {
            return converterBuilders.get(conversionWord);
        }

        public LogEventFormatter create(PatternConverterSpec spec) {
            String conversionWord = spec.getConversionWord();
            ConverterBuilder function = converterBuilders.get(conversionWord);
            if (function == null) {
                throw new IllegalArgumentException("Unknown conversion word <%" + conversionWord + "> not in " + getConversionWords());
            }

            try {
                return function.apply(spec);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("While building <%" + conversionWord + "> with parameters " + spec.getParameters(), e);
            }
        }

        public Collection<String> getConversionWords() {
            return converterBuilders.keySet();
        }
    }

    private static ConverterBuilderFactory factory = new ConverterBuilderFactory();

    private static ConsoleFormatting ansiFormat = ConsoleFormatting.getInstance();

    static {
        factory.put("logger", spec -> {
            Optional<Integer> length = spec.getIntParameter(0);
            return e -> e.getLoggerName(length);
        });
        factory.putAliases("logger", new String[] { "c", "lo" });

        factory.put("class", spec -> e -> e.getCallerClassName()); // TODO: int parameter
        factory.putAliases("class", new String[] { "C" });

        factory.put("method", spec -> e -> e.getCallerMethodName());
        factory.putAliases("method", new String[] { "M" });

        factory.put("file", spec -> e -> e.getCallerFileName());
        factory.putAliases("file", new String[] { "F" });

        factory.put("line", spec -> e -> String.valueOf(e.getCallerLine()));
        factory.putAliases("line", new String[] { "L" });

        factory.put("date", spec -> {
            DateTimeFormatter formatter = spec.getParameter(0)
                    .map(DateTimeFormatter::ofPattern)
                    .orElse(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            ZoneId zone = spec.getParameter(1)
                    .map(ZoneId::of)
                    .orElse(ZoneId.systemDefault());
            return e -> formatter.format(e.getInstant().atZone(zone));
        });
        factory.putAliases("date", new String[] { "d" });


        factory.put("level", spec -> e -> e.getLevel().toString());
        factory.put("message", spec -> e -> e.formatMessage());
        factory.putAliases("message", new String[] { "m", "msg" });
        factory.put("thread", spec -> e -> e.getThreadName());
        factory.putAliases("thread", new String[] { "t" });

//        factory.putExceptionHandler("exception", spec -> {
//            BiFunction<Throwable, Optional<Integer>, String> throwableFormatter = spec.getThrowableFormatter();
//            Optional<Integer> length = spec.getIntParameter(0);
//            return e -> e.getThrowable() != null ? throwableFormatter.apply(e.getThrowable(), length) : "";
//        });
//        factory.putAliases("exception", new String[] { "ex", "throwable" });

        factory.put("mdc", spec -> {
            if (spec.getParameters().isEmpty()) {
                return e -> e.getMdc();
            } else {
                String[] parts = spec.getParameters().get(0).split(":-");
                String key = parts[0];
                String defaultValue = parts.length > 1 ? parts[1] : "";
                return e -> e.getMdc(key, defaultValue);
            }
        });

        factory.putTransformer("replace", spec -> {
            String regex = spec.getParameters().get(0);
            String replacement = spec.getParameters().get(1);
            return s -> s.replaceAll(regex, replacement);
        });


        // TODO

        //  relative / r - Outputs the number of milliseconds elapsed since the start of the application until the creation of the logging event.

        //  marker
        //  caller
        //  ?? property

        factory.put("highlight", spec -> {
            LogEventFormatter nestedFormatter = spec.getSubpattern().orElse(e -> "");
            return e -> {
                return ansiFormat.highlight(e.getLevel(), nestedFormatter.format(e));
            };
        });

        factory.putTransformer("black", spec -> s -> ansiFormat.black(s));
        factory.putTransformer("red", spec -> s -> ansiFormat.red(s));
        factory.putTransformer("green", spec -> s -> ansiFormat.green(s));
        factory.putTransformer("yellow", spec -> s -> ansiFormat.yellow(s));
        factory.putTransformer("blue", spec -> s -> ansiFormat.blue(s));
        factory.putTransformer("magenta", spec -> s -> ansiFormat.magenta(s));
        factory.putTransformer("cyan", spec -> s -> ansiFormat.cyan(s));
        factory.putTransformer("white", spec -> s -> ansiFormat.white(s));

        factory.putTransformer("boldBlack", spec -> s -> ansiFormat.boldBlack(s));
        factory.putTransformer("boldRed", spec -> s -> ansiFormat.boldRed(s));
        factory.putTransformer("boldGreen", spec -> s -> ansiFormat.boldGreen(s));
        factory.putTransformer("boldYellow", spec -> s -> ansiFormat.boldYellow(s));
        factory.putTransformer("boldBlue", spec -> s -> ansiFormat.boldBlue(s));
        factory.putTransformer("boldMagenta", spec -> s -> ansiFormat.boldMagenta(s));
        factory.putTransformer("boldCyan", spec -> s -> ansiFormat.boldCyan(s));
        factory.putTransformer("boldWhite", spec -> s -> ansiFormat.boldWhite(s));
    }

    private String pattern;
    private final ExceptionFormatter exceptionFormatter;

    private LogEventFormatter converter;

    public Collection<String> getConversionWords() {
        return factory.getConversionWords();
    }

    public PatternLogEventFormatter(String pattern) {
        setPattern(pattern);
        this.exceptionFormatter = new ExceptionFormatter();
    }

    public PatternLogEventFormatter(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public PatternLogEventFormatter(Configuration configuration) {
        setPattern(configuration.getString("pattern"));
        this.exceptionFormatter = configuration.createInstanceWithDefault("exceptionFormatter", ExceptionFormatter.class);
    }

    public ExceptionFormatter getExceptionFormatter() {
        return exceptionFormatter;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
        this.converter = readConverter(new StringScanner(pattern), '\0');
    }

    LogEventFormatter readConverter(StringScanner scanner, char terminator) {
        List<LogEventFormatter> converters = new ArrayList<>();
        while (scanner.hasMoreCharacters() && scanner.current() != terminator) {
            // TODO: Escaped %
            String text = scanner.readUntil(terminator, '%');
            if (text.length() > 0) {
                converters.add(getConstant(text.toString()));
            }
            if (scanner.hasMoreCharacters() && scanner.current() != terminator) {
                PatternConverterSpec patternSpec = new PatternConverterSpec(scanner);
                patternSpec.readConversion(this);
                converters.add(factory.create(patternSpec));
            }
        }
        return compositeFormatter(converters);
    }

    private static LogEventFormatter compositeFormatter(List<LogEventFormatter> converters) {
        return event -> converters.stream()
                .map(converter -> converter.format(event))
                .collect(Collectors.joining(""));
    }

    @Override
    public String format(LogEvent event) {
        return converter.format(event) +
                (event.getThrowable() != null ? "\n" +exceptionFormatter.format(event.getThrowable()) : "");
    }

    private static LogEventFormatter getConstant(String string) {
        return e -> string;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + pattern + "}";
    }

}
