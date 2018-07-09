package org.logevents.destinations;

import org.logevents.LogEvent;

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

    static LogEventFormatter withDefaultFormat() {
        return new LogEventFormatter() {
            @Override
            public String format(LogEvent e) {
                return String.format("%s [%s] [%s] [%s]: %s\n",
                        e.getZonedDateTime().toLocalTime(), e.getThreadName(), LogEventFormatter.leftPad(e.getLevel(), 5, ' '), e.getLoggerName(), e.formatMessage());
            }
        };
    }

}
