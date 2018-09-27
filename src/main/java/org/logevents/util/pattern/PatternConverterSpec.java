package org.logevents.util.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.logevents.formatting.PatternLogEventFormatter;

/**
 * Used to parse a single conversion for {@link PatternLogEventFormatter}. A
 * conversion is on the format
 * "%[&lt;minlength&gt;][.&lt;maxlength&gt;]&lt;conversion word&gt;[(&lt;conversion subpattern&gt;)][{&lt;parameter&gt;,&lt;parameter&gt;}]".
 *
 * @author Johannes Brodwall
 *
 */
public class PatternConverterSpec<T extends Function<?, String>> {

    private Optional<Integer> minLength;
    private Optional<Integer> maxLength;
    private String conversionWord;
    private List<String> parameters = new ArrayList<>();
    private Optional<T> subpattern = Optional.empty();
    private StringScanner scanner;
    private BiFunction<Throwable, Optional<Integer>, String> throwableFormatter;

    public PatternConverterSpec(StringScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Reads all the internal state from the scanner. Can only be called
     * once as it doesn't rewind the scanner.
     * @param formatter Used for sub-patterns
     */
    public void readConversion(PatternReader<T> formatter) {
        readConversion();
        try {
            readSubpattern(formatter);
            readParameters();
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("End of string while reading <%" + getConversionWord() + "> from " + scanner);
        }
    }

    public void readConversion() {
        scanner.advance();
        readMinLength();
        readMaxLength();
        readConversionWord();
    }

    /**
     * If the resulting string is shorter than abs(min)-length, it should be padded.
     * If minLength is negative, the string should be right-padded, otherwise, it
     * should be left padded. If minLength is {@link Optional#empty()}, output will not be padded.
     */
    public Optional<Integer> getMinLength() {
        return minLength;
    }

    /**
     * If the resulting string is longer than abs(min)-length, it should be truncated.
     * If maxLength is negative, the string should be truncated on the right, otherwise, it
     * should be truncated on the left. If maxLength is {@link Optional#empty()}, output will not be truncated.
     */
    public Optional<Integer> getMaxLength() {
        return maxLength;
    }

    /**
     * The function represented is represented by the conversion word. The full list
     * of conversion words are given in {@link PatternLogEventFormatter#getConversionWords}
     */
    public String getConversionWord() {
        return conversionWord;
    }

    /**
     * A fully parsed sub pattern specified in parenthesis () after the conversion word.
     * The string used in the subpattern can contain further conversions of its own.
     */
    public Optional<T> getSubpattern() {
        return subpattern;
    }

    /**
     * A list of parameters specified in curly brackets after the conversion word.
     * The parameters are separated by , and can optionally by quoted with single quotes (').
     * For example %date{ 'HH:mm:ss,SSS', Europe/Oslo} has the parameters "HH:mm:ss,SSS" and
     * "Europe/Oslo"
     */
    public List<String> getParameters() {
        return parameters;
    }

    public Optional<String> getParameter(int i) {
        return i < parameters.size() ? Optional.of(parameters.get(i)) : Optional.empty();
    }

    public Optional<Integer> getIntParameter(int i) {
        return getParameter(i).map(Integer::parseInt);
    }

    private void readSubpattern(PatternReader<T> parser) {
        // TODO: Should only read subpattern for %replace and %<colors>, so that
        //   it's possible to do %file(%line)
        if (scanner.current() == '(')  {
            scanner.advance();
            this.subpattern = Optional.of(parser.readConverter(scanner, ')'));
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
        while (Character.isAlphabetic(scanner.current())) {
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

        String param = scanner.readUntil(',', '}');
        parameters.add(param);
        return scanner.advance() == ',';
    }

    private boolean readQuotedParameter() {
        // TODO: Escaped quotes
        char quote = scanner.advance();
        parameters.add(scanner.readUntil(quote));
        scanner.advance();
        scanner.skipWhitespace();
        return scanner.advance() == ',';
    }

    public BiFunction<Throwable, Optional<Integer>, String> getThrowableFormatter() {
        return throwableFormatter;
    }
}
