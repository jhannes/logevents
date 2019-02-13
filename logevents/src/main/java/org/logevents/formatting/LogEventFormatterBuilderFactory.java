package org.logevents.formatting;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.logevents.util.pattern.PatternConverterSpec;
import org.logevents.util.pattern.PatternFactory;

public class LogEventFormatterBuilderFactory implements PatternFactory<LogEventFormatter> {
    private Map<String, LogEventFormatterBuilder> converterBuilders = new HashMap<>();

    @Override
    public LogEventFormatter compositeFormatter(List<LogEventFormatter> converters) {
        return event -> converters.stream()
                .map(converter -> converter.apply(event))
                .collect(Collectors.joining(""));
    }

    @Override
    public LogEventFormatter getConstant(String string) {
        return e -> string;
    }

    public void putWithoutLengthRestriction(String conversionWord, LogEventFormatterBuilder converterBuilder) {
        converterBuilders.put(conversionWord, converterBuilder);
    }

    public void putAliases(String conversionWord, String[] aliases) {
        for (String alias : aliases) {
            converterBuilders.put(alias, converterBuilders.get(conversionWord));
        }
    }

    public void put(String conversionWord, LogEventFormatterBuilder converterBuilder) {
        putWithoutLengthRestriction(conversionWord,
                spec -> {
                    LogEventFormatter f = converterBuilder.apply(spec);
                    Optional<Integer> minLength = spec.getMinLength(),
                            maxLength = spec.getMaxLength();
                    return e -> restrictLength(f.apply(e), minLength, maxLength);
                });
    }

    public void putTransformer(String conversionWord, Function<PatternConverterSpec<LogEventFormatter>, Function<String, String>> transformerBuilder) {
        put(conversionWord, spec -> {
           LogEventFormatter nestedFormatter = spec.getSubpattern().orElse(e -> "");
           Function<String, String> transformer = transformerBuilder.apply(spec);
           return e -> transformer.apply(nestedFormatter.apply(e));
        });
    }

    public LogEventFormatterBuilder get(String conversionWord) {
        return converterBuilders.get(conversionWord);
    }

    @Override
    public LogEventFormatter create(PatternConverterSpec<LogEventFormatter> spec) {
        String conversionWord = spec.getConversionWord();
        LogEventFormatterBuilder function = converterBuilders.get(conversionWord);
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

    static String restrictLength(String s, Optional<Integer> minLength, Optional<Integer> maxLength) {
        return truncate(pad(s, minLength), maxLength);
    }

    static String truncate(String s, Optional<Integer> maxLengthOpt) {
        if (!maxLengthOpt.isPresent()) {
            return s;
        }
        int maxLength = maxLengthOpt.get();
        if (s.length() <= Math.abs(maxLength)) {
            return s;
        } else if (maxLength > 0) {
            return s.substring(0, maxLength);
        } else {
            return s.substring(s.length() + maxLength);
        }
    }

    static String pad(String s, Optional<Integer> minimumLength) {
        if (!minimumLength.isPresent()) {
            return s;
        }
        int padding = minimumLength.get();
        if (Math.abs(padding) < s.length()) {
            return s;
        } else if (padding > 0) {
            return repeat(Math.abs(padding) - s.length(), ' ') + s;
        } else {
            return s + repeat(Math.abs(padding) - s.length(), ' ');
        }
    }

    static String repeat(int count, char padChar) {
        char[] result = new char[count];
        for (int i = 0; i < count; i++) {
            result[i] = padChar;
        }
        return new String(result);
    }


}