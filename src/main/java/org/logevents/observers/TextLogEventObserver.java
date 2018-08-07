package org.logevents.observers;

import java.io.IOException;
import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.destinations.LogEventDestination;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;

/**
 * Formats each {@link LogEvent} with the specified {@link LogEventFormatter}
 * and forwards them to the specified {@link LogEventDestination}. Most common
 * usage is to output to a file or system out.
 *
 * @author Johannes Brodwall
 */
public class TextLogEventObserver implements LogEventObserver {

    private final LogEventDestination destination;
    private final LogEventFormatter formatter;

    public TextLogEventObserver(Properties properties, String prefix) {
        Configuration configuration = new Configuration(properties, prefix);
        destination = configuration.createInstance("destination", LogEventDestination.class);
        formatter = configuration.createInstance("formatter", LogEventFormatter.class);
        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

    public TextLogEventObserver(LogEventDestination destination, LogEventFormatter formatter) {
        this.destination = destination;
        this.formatter = formatter;
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        try {
            destination.writeEvent(formatter.format(logEvent));
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to write log event", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{destination=" + destination
                + ",formatter=" + formatter + "}";
    }

}
