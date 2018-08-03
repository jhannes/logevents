package org.logevents.destinations;

import static org.logevents.destinations.LogEventFormatter.restrictLength;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.logevents.destinations.PatternLogEventFormatter.FormattingFunction;

class PatternConverterBuilder {

    private Optional<Integer> padding;
    private Optional<Integer> maxLength = Optional.empty();
    private String conversionWord;
    private List<String> parameters = new ArrayList<>();
    private Optional<LogEventFormatter> subpattern = Optional.empty();
    private StringScanner scanner;

    public PatternConverterBuilder(String pattern, int startIndex) {
        this.scanner = new StringScanner(pattern, startIndex);
    }

    public PatternConverterBuilder(StringScanner scanner) {
        this.scanner = scanner;
    }

    Optional<LogEventFormatter> readConversion(PatternLogEventFormatter formatter) {
        if (scanner.hasMoreCharacters()) {
            scanner.advance();
            readMinLength();
            readMaxLength();
            readConversionWord();
            readSubpattern(formatter);
            readParameters();

            return Optional.of(createConverter());
        } else {
            return Optional.empty();
        }
    }

    private void readSubpattern(PatternLogEventFormatter formatter) {
        if (scanner.current() == '(')  {
            scanner.advance();
            this.subpattern = Optional.of(readPattern(formatter, ')'));
        }
    }

    private LogEventFormatter readPattern(PatternLogEventFormatter formatter, char terminator) {
        String string = readUntil(terminator);
        scanner.advance();
        return event -> string;
    }

    private void readMaxLength() {
        this.maxLength = Optional.empty();
        if (scanner.current() == '.') {
            scanner.advance();
            maxLength = readInteger();
        }
    }

    private void readMinLength() {
        this.padding = readInteger();
    }

    private void readConversionWord() {
        StringBuilder result = new StringBuilder();
        while (scanner.hasMoreCharacters() ) {
            if (!Character.isAlphabetic(scanner.current())) break;
            result.append(scanner.advance());
        }
        this.conversionWord = result.toString();
    }

    private void readParameters() {
        if (scanner.current() == '{')  {
            scanner.advance();
            while (readSingleParameter()) {}
        }
    }

    private boolean readSingleParameter() {
        skipWhitespace();
        if (scanner.current() == '\'') {
            return readQuotedParameter();
        }
        StringBuilder parameter = new StringBuilder();
        while (scanner.hasMoreCharacters()) {
            if (scanner.current() == ',' || scanner.current() == '}') {
                parameters.add(parameter.toString());
                return scanner.advance() == ',';
            } else {
                parameter.append(scanner.advance());
            }
        }
        return false;
    }

    private boolean readQuotedParameter() {
        char quote = scanner.advance();
        parameters.add(readUntil(quote));
        scanner.advance();
        skipWhitespace();
        return scanner.advance() == ',';
    }


    private String readUntil(char terminator) {
        StringBuilder parameter = new StringBuilder();
        while (scanner.hasMoreCharacters()) {
            if (scanner.current() == terminator) {
                break;
            }
            parameter.append(scanner.advance());
        }
        return parameter.toString();
    }


    private void skipWhitespace() {
        while (Character.isWhitespace(scanner.current())) {
            scanner.advance();
        }
    }

    private Optional<Integer> readInteger() {
        StringBuilder number = new StringBuilder();
        if (scanner.current() == '-') {
            number.append(scanner.advance());
        }
        while (scanner.hasMoreCharacters()) {
            if (!Character.isDigit(scanner.current())) break;
            number.append(scanner.advance());
        }
        if (number.length() > 0) {
            return Optional.of(Integer.parseInt(number.toString()));
        }
        return Optional.empty();
    }

    public int getEndIndex() {
        return scanner.getPosition();
    }

    private LogEventFormatter createConverter() {
        return getFormattingFunction(conversionWord)
                .orElseGet(() ->
                    getSimpleFunction(conversionWord)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown conversion " + conversionWord)));
    }

    private Optional<LogEventFormatter> getFormattingFunction(String pattern) {
        return PatternLogEventFormatter.getFormattingFunction(pattern).map(f -> curry(f, padding, maxLength, subpattern, parameters));
    }

    private Optional<LogEventFormatter> getSimpleFunction(String pattern) {
        return PatternLogEventFormatter.getSimpleFunction(pattern).map(f -> decorate(f, padding, maxLength));
    }

    private static LogEventFormatter decorate(LogEventFormatter formatter, Optional<Integer> padding, Optional<Integer> maxLength) {
        return event -> restrictLength(formatter.format(event), padding, maxLength);
    }

    private static LogEventFormatter curry(FormattingFunction f, Optional<Integer> padding, Optional<Integer> maxLength, Optional<LogEventFormatter> subpattern, List<String> parameters) {
        return event -> f.format(event, padding, maxLength, subpattern, parameters);
    }

}