package org.logevents.observers;

import java.io.IOException;
import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.destinations.LogEventDestination;
import org.logevents.destinations.LogEventFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ConfigUtil;

public class TextLogEventObserver implements LogEventObserver {

    private final LogEventDestination eventDestination;
    private final LogEventFormatter logEventFormatter;

    public TextLogEventObserver(Properties configuration, String prefix) {
        eventDestination = ConfigUtil.create(prefix + ".eventDestination", "org.logevents.destinations", configuration);
        logEventFormatter = ConfigUtil.create(prefix + ".logEventFormatter", "org.logevents.destinations", configuration);
        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

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

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{eventDestination=" + eventDestination
                + ",logEventFormatter=" + logEventFormatter + "}";
    }

}
