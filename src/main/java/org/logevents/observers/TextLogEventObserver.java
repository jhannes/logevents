package org.logevents.observers;

import java.io.IOException;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.destinations.LogEventDestination;
import org.logevents.destinations.LogEventFormatter;

public class TextLogEventObserver implements LogEventObserver {

    private LogEventDestination eventDestination;
    private LogEventFormatter logEventFormatter;

    public TextLogEventObserver(LogEventDestination eventDestination, LogEventFormatter logEventFormatter) {
        this.eventDestination = eventDestination;
        this.logEventFormatter = logEventFormatter;
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        try {
            eventDestination.writeEvent(logEventFormatter.format(logEvent));
        } catch (IOException e) {
            // PANICK!
            e.printStackTrace();
        }
    }

}
