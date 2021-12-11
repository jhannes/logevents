package org.logevents.formatters.pattern;

import org.logevents.config.Configuration;
import org.logevents.formatters.PatternLogEventFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Used to parse a single conversion for {@link PatternReader}. A
 * conversion is on the format
 * "%[&lt;minlength&gt;][.&lt;maxlength&gt;]&lt;conversion word&gt;[{&lt;parameter&gt;,&lt;parameter&gt;}]".
 *
 * @author Johannes Brodwall
 *
 */
public class PatternConverterSpec {
    protected final Configuration configuration;
    protected StringScanner scanner;
    private Optional<Integer> minLength;
    private Optional<Integer> maxLength;
    private String conversionWord;
    private List<String> parameters = new ArrayList<>();
    private BiFunction<Throwable, Optional<Integer>, String> throwableFormatter;

    public PatternConverterSpec(Configuration configuration, StringScanner scanner) {
        this.configuration = configuration;
        this.scanner = scanner;
    }

    public void readConversion() {
        advance();
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

    private void readMaxLength() {
        this.maxLength = Optional.empty();
        if (scanner.current() == '.') {
            advance();
            maxLength = scanner.readInteger();
        }
    }

    private void readMinLength() {
        this.minLength = scanner.readInteger();
    }

    private void readConversionWord() {
        StringBuilder result = new StringBuilder();
        while (Character.isAlphabetic(scanner.current())) {
            result.append(advance());
        }
        this.conversionWord = result.toString();
    }

    public void readParameters() {
        if (scanner.current() == '{')  {
            advance();
            //noinspection StatementWithEmptyBody
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
        return advance() == ',';
    }

    char advance() {
        if (!scanner.hasMoreCharacters()) {
            throw new IllegalArgumentException("End of string while reading <%" + getConversionWord() + "> from " + scanner);
        }
        return scanner.advance();
    }

    private boolean readQuotedParameter() {
        // TODO: Escaped quotes
        char quote = advance();
        parameters.add(scanner.readUntil(quote));
        advance();
        scanner.skipWhitespace();
        return advance() == ',';
    }

    public BiFunction<Throwable, Optional<Integer>, String> getThrowableFormatter() {
        return throwableFormatter;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public String toString() {
        return "PatternConverterSpec{" +
                "conversionWord='" + conversionWord + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
