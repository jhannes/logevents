package org.logevents.observers;

import java.io.IOException;
import java.util.Properties;

import org.logevents.destinations.DateRollingFileDestination;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;

/**
 * Log events to a file with the date appended to the fileName pattern.
 * Convenience class used to create a {@link TextLogEventObserver}
 * with a {@link DateRollingFileDestination} and a suitable default
   {@link LogEventFormatter}.
 *
 * @author Johannes Brodwall
 */
public class DateRollingLogEventObserver extends TextLogEventObserver {

    public DateRollingLogEventObserver(String fileName, LogEventFormatter formatter) throws IOException {
        super(new DateRollingFileDestination(fileName), formatter);
    }

    public DateRollingLogEventObserver(String fileName) throws IOException {
        this(fileName, new TTLLEventLogFormatter());
    }

    public DateRollingLogEventObserver(Configuration configuration) throws IOException {
        this(configuration.getString("filename"),
                configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, TTLLEventLogFormatter.class));
        LogEventStatus.getInstance().addInfo(this, "Configured " + configuration.getPrefix());
    }

    public DateRollingLogEventObserver(Properties properties, String prefix) throws IOException {
        this(new Configuration(properties, prefix));
    }
}
