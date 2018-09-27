package org.logevents.util.pattern;

import java.util.List;
import java.util.function.Function;

public interface PatternFactory<T extends Function<?, String>> {

    T getConstant(String string);

    T create(PatternConverterSpec<T> patternSpec);

    T compositeFormatter(List<T> converters);

}
