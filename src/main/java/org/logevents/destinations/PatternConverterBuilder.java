package org.logevents.destinations;

import static org.logevents.destinations.LogEventFormatter.restrictLength;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.logevents.destinations.PatternLogEventFormatter.FormattingFunction;

/**
 * Used to parse a single conversion for {@link PatternLogEventFormatter}. A
 * conversion is on the format
 * "%[&lt;minlength&gt;][.&lt;maxlength&gt;]&lt;conversion word&gt;[(&lt;conversion subpattern&gt;)][{&lt;parameter&gt;,&lt;parameter&gt;}]".
 *
 * @author Johannes Brodwall
 *
 */
class PatternConverterBuilder {

    private Optional<Integer> minLength;
    private Optional<Integer> maxLength;
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
            this.subpattern = Optional.of(formatter.readConverter(scanner, ')'));
            scanner.advance();
        }
    }

    private void readMaxLength() {
        this.maxLength = Optional.empty();
        if (scanner.current() == '.') {
            scanner.advance();
            maxLength = scanner.readInteger();
        }
    }

    private void readMinLength() {
        this.minLength = scanner.readInteger();
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
        scanner.skipWhitespace();
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
        parameters.add(scanner.readUntil(quote));
        scanner.advance();
        scanner.skipWhitespace();
        return scanner.advance() == ',';
    }

    private LogEventFormatter createConverter() {
        return getFormattingFunction(conversionWord)
                .orElseGet(() ->
                    getSimpleFunction(conversionWord)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown conversion " + conversionWord)));
    }

    private Optional<LogEventFormatter> getFormattingFunction(String pattern) {
        return PatternLogEventFormatter.getFormattingFunction(pattern).map(f -> curry(f, minLength, maxLength, subpattern, parameters));
    }

    private Optional<LogEventFormatter> getSimpleFunction(String pattern) {
        return PatternLogEventFormatter.getSimpleFunction(pattern).map(f -> decorate(f, minLength, maxLength));
    }

    private static LogEventFormatter decorate(LogEventFormatter formatter, Optional<Integer> padding, Optional<Integer> maxLength) {
        return event -> restrictLength(formatter.format(event), padding, maxLength);
    }

    private static LogEventFormatter curry(FormattingFunction f, Optional<Integer> padding, Optional<Integer> maxLength, Optional<LogEventFormatter> subpattern, List<String> parameters) {
        return event -> f.format(event, padding, maxLength, subpattern, parameters);
    }

}