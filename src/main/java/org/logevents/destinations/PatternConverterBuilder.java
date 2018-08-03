package org.logevents.destinations;

import static org.logevents.destinations.LogEventFormatter.restrictLength;

import java.util.Optional;

import org.logevents.destinations.PatternLogEventFormatter.FormattingFunction;

class PatternConverterBuilder {

    private int endIndex;
    private Optional<LogEventFormatter> converter;
    private String pattern;
    private Optional<Integer> padding;
    private Optional<Integer> maxLength = Optional.empty();

    public PatternConverterBuilder(String pattern, int startIndex) {
        this.endIndex = startIndex + 1;
        this.pattern = pattern;
        if (endIndex < pattern.length()) {
            this.padding = readInteger();
            this.maxLength = Optional.empty();
            if (current() == '.') {
                advance();
                maxLength = readInteger();
            }
            converter = Optional.of(createConverter(readFunctionName()));
        } else {
            converter = Optional.empty();
        }
    }

    private Optional<Integer> readInteger() {
        StringBuilder number = new StringBuilder();
        if (current() == '-') {
            number.append(advance());
        }
        while (endIndex < pattern.length()) {
            if (!Character.isDigit(current())) break;
            number.append(advance());
        }
        if (number.length() > 0) {
            return Optional.of(Integer.parseInt(number.toString()));
        }
        return Optional.empty();
    }

    private String readFunctionName() {
        int startIndex = endIndex;
        while (endIndex < pattern.length() ) {
            if (!Character.isAlphabetic(current())) break;
            endIndex++;
        }
        return pattern.substring(startIndex, endIndex);
    }

    public Optional<LogEventFormatter> getConverter() {
        return converter;
    }

    private char advance() {
        return pattern.charAt(endIndex++);
    }

    private char current() {
        return pattern.charAt(endIndex);
    }

    public int getEndIndex() {
        return endIndex;
    }

    private LogEventFormatter createConverter(String pattern) {
        return getFormattingFunction(pattern)
                .orElseGet(() ->
                    getSimpleFunction(pattern)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown conversion " + pattern)));
    }

    private Optional<LogEventFormatter> getFormattingFunction(String pattern) {
        return PatternLogEventFormatter.getFormattingFunction(pattern).map(f -> curry(f, padding, maxLength));
    }

    private Optional<LogEventFormatter> getSimpleFunction(String pattern) {
        return PatternLogEventFormatter.getSimpleFunction(pattern).map(f -> decorate(f, padding, maxLength));
    }

    private static LogEventFormatter decorate(LogEventFormatter formatter, Optional<Integer> padding, Optional<Integer> maxLength) {
        return event -> restrictLength(formatter.format(event), padding, maxLength);
    }

    private static LogEventFormatter curry(FormattingFunction f, Optional<Integer> padding, Optional<Integer> maxLength) {
        return event -> f.format(event, padding, maxLength);
    }

}