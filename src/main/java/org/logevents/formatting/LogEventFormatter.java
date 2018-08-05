package org.logevents.formatting;

import java.util.Optional;

import org.logevents.LogEvent;

/**
 * Represents a strategy for converting a LogEvent to a string.
 *
 * @author Johannes Brodwall
 *
 */
public interface LogEventFormatter {

    String format(LogEvent logEvent);

    static String restrictLength(String s, Optional<Integer> minLength, Optional<Integer> maxLength) {
        return truncate(pad(s, minLength), maxLength);
    }

    static String truncate(String s, Optional<Integer> maxLengthOpt) {
        if (!maxLengthOpt.isPresent()) {
            return s;
        }
        int maxLength = maxLengthOpt.get();
        if (s.length() <= Math.abs(maxLength)) {
            return s;
        } else if (maxLength > 0) {
            return s.substring(0, maxLength);
        } else {
            return s.substring(s.length() + maxLength);
        }
    }

    static String pad(String s, Optional<Integer> minimumLength) {
        if (!minimumLength.isPresent()) {
            return s;
        }
        int padding = minimumLength.get();
        if (Math.abs(padding) < s.length()) {
            return s;
        } else if (padding > 0) {
            return repeat(Math.abs(padding) - s.length(), ' ') + s;
        } else {
            return s + repeat(Math.abs(padding) - s.length(), ' ');
        }
    }

    static String leftPad(Object o, int length, char padChar) {
        String value = String.valueOf(o);
        return value.length() >= length ? value : LogEventFormatter.repeat(length - value.length(), padChar) + value;
    }

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
