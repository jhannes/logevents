package org.logevents.extend.ansi;

import org.fusesource.jansi.AnsiConsole;
import org.logevents.LogEvent;
import org.logevents.destinations.LogEventFormatter;

public class AnsiLogEventFormatter implements LogEventFormatter {

    static {
        AnsiConsole.systemInstall();
    }

    @Override
    public String format(LogEvent e) {
        return String.format("%s [%s] [%s] [%s]: %s\n",
                e.getZonedDateTime().toLocalTime(),
                e.getThreadName(),
                green(LogEventFormatter.leftPad(e.getLevel(), 5, ' ')),
                bold(e.getLoggerName()),
                e.formatMessage())
                + LogEventFormatter.stackTrace(e);
    }

    protected String bold(String s) {
        return String.format("\033[1;m%s\033[m", s);
    }

    protected String green(String s) {
        return String.format("\033[32m%s\033[m", s);
    }

}
