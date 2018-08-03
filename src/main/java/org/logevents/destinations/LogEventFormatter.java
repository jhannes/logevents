package org.logevents.destinations;

import java.util.Optional;

import org.logevents.LogEvent;

public interface LogEventFormatter {

    String format(LogEvent logEvent);

    static LogEventFormatter withDefaultFormat() {
        return new TTLLEventLogFormatter();
    }

    static String restrictLength(String s, Optional<Integer> minimumLength, Optional<Integer> maximumLength) {
        return truncate(pad(s, minimumLength), maximumLength);
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
