package org.logevents.formatters.pattern;

import org.logevents.config.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class PatternReader<T extends Function<?, String>> {

    private PatternFactory<T> factory;
    private Configuration configuration;

    public PatternReader(Configuration configuration, PatternFactory<T> factory) {
        this.configuration = configuration;
        this.factory = factory;
    }

    public T readPattern(String pattern) {
        return readConverter(new StringScanner(pattern), '\0');
    }

    public T readConverter(StringScanner scanner, char terminator) {
        List<T> converters = new ArrayList<>();
        while (scanner.hasMoreCharacters() && scanner.current() != terminator) {
            // TODO: Escaped %
            String text = scanner.readUntil(terminator, '%');
            if (text.length() > 0) {
                converters.add(factory.getConstant(text));
            }
            if (scanner.hasMoreCharacters() && scanner.current() != terminator) {
                PatternConverterSpecWithSubpattern<T> patternSpec = new PatternConverterSpecWithSubpattern<>(configuration, scanner);
                patternSpec.readConversion();
                readSubpattern(patternSpec);
                patternSpec.readParameters();
                converters.add(factory.create(patternSpec));
            }
        }
        return factory.compositeFormatter(converters);
    }

    private void readSubpattern(PatternConverterSpecWithSubpattern<T> patternSpec) {
        // TODO: Should only read subpattern for %replace and %<colors>, so that
        //   it's possible to do %file(%line)
        if (patternSpec.scanner.current() == '(')  {
            patternSpec.advance();
            patternSpec.setSubpattern(Optional.of(readConverter(patternSpec.scanner, ')')));
            patternSpec.advance();
        }
    }

}
