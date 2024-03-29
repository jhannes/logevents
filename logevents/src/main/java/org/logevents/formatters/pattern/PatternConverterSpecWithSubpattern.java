    package org.logevents.formatters.pattern;

import org.logevents.config.Configuration;
import org.logevents.formatters.PatternLogEventFormatter;

import java.util.Optional;
import java.util.function.Function;

/**
 * Used to parse a single conversion for {@link PatternLogEventFormatter}. A
 * conversion is on the format
 * "<code>%[&lt;minlength&gt;][.&lt;maxlength&gt;]&lt;conversion word&gt;[(&lt;conversion subpattern&gt;)][{&lt;parameter&gt;,&lt;parameter&gt;}]</code>".
 *
 * @author Johannes Brodwall
 *
 */
public class PatternConverterSpecWithSubpattern<T extends Function<?, String>> extends PatternConverterSpec {

    private Optional<T> subpattern = Optional.empty();

    public PatternConverterSpecWithSubpattern(Configuration configuration, StringScanner scanner) {
        super(configuration, scanner);
    }

    /**
     * A fully parsed sub pattern specified in parenthesis () after the conversion word.
     * The string used in the subpattern can contain further conversions of its own.
     */
    public Optional<T> getSubpattern() {
        return subpattern;
    }

    public void setSubpattern(Optional<T> subpattern) {
        this.subpattern = subpattern;
    }
}
