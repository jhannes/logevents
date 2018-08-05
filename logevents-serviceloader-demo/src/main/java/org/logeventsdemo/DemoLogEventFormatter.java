package org.logeventsdemo;

import org.logevents.LogEvent;
import org.logevents.formatting.LogEventFormatter;

public class DemoLogEventFormatter implements LogEventFormatter {

    @Override
    public String format(LogEvent logEvent) {
        return "AN ERROR! " + "(" + logEvent.getLevel() + "): " + logEvent.formatMessage() + "\n" + "\tCalled at: "
                + logEvent.getCallerLocation();
    }

}
