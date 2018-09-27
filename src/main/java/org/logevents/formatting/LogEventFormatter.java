package org.logevents.formatting;

import java.util.function.Function;

import org.logevents.LogEvent;

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


}
