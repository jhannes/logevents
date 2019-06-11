package org.logevents.formatting;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a strategy for converting a LogEvent to a string.
 *
 * @author Johannes Brodwall
 *
 */
public interface LogEventFormatter extends Function<LogEvent, String> {

    @Override
    String apply(LogEvent logEvent);

    static String rightPad(Object o, int length, char padChar) {
        String value = String.valueOf(o);
        return value.length() >= length ? value : value + LogEventFormatter.repeat(length - value.length(), padChar);
    }

    static String repeat(int count, char padChar) {
        char[] result = new char[count];
        for (int i = 0; i < count; i++) {
            result[i] = padChar;
        }
        return new String(result);
    }

    default Optional<ExceptionFormatter> getExceptionFormatter() {
        return Optional.empty();
    }

    default String mdc(LogEvent e, String[] includeMdcKeys) {
        List<String> mdcValue = new ArrayList<>();
        if (includeMdcKeys == null) {
            e.getMdcProperties().forEach((k, v) -> mdcValue.add(k + "=" + v));
        } else {
            for (String key : includeMdcKeys) {
                if (e.getMdcProperties().containsKey(key)) {
                    mdcValue.add(key + "=" + e.getMdcProperties().get(key));
                }
            }
        }
        return mdcValue.isEmpty() ? "" : " {" + String.join(", ", mdcValue) + "}";
    }

    default void configure(Configuration configuration) {
    }
}
