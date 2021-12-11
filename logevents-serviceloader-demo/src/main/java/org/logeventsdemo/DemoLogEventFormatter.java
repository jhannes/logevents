package org.logeventsdemo;

import org.logevents.LogEvent;
import org.logevents.formatters.LogEventFormatter;

public class DemoLogEventFormatter implements LogEventFormatter {

    @Override
    public String apply(LogEvent logEvent) {
        return "AN ERROR! " + "(" + logEvent.getLevel() + "): " + logEvent.formatMessage() + "\n" + "\tCalled at: "
                + logEvent.getCallerLocation();
    }

}
