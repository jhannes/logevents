package org.logevents.destinations;

import org.logevents.LogEvent;
import org.slf4j.event.Level;

public interface LogEventFormatter {

    String format(LogEvent logEvent);

    static String repeat(int count, char padChar) {
        char[] result = new char[count];
        for (int i = 0; i < count; i++) {
            result[i] = padChar;
        }
        return new String(result);
    }

    static String leftPad(Object o, int length, char padChar) {
        String value = String.valueOf(o);
        return value.length() >= length ? value : LogEventFormatter.repeat(length - value.length(), padChar) + value;
    }

    static String rightPad(Object o, int length, char padChar) {
        String value = String.valueOf(o);
        return value.length() >= length ? value : value + LogEventFormatter.repeat(length - value.length(), padChar);
    }

    static LogEventFormatter withDefaultFormat() {
        return new TTLLEventLogFormatter();
    }


}
