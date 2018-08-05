package org.logevents.observers;

import java.io.IOException;
import java.util.Properties;

import org.logevents.destinations.DateRollingFileDestination;
import org.logevents.destinations.LogEventFormatter;
import org.logevents.destinations.TTLLEventLogFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ConfigUtil;

/**
 * Log events to a file with the date appended to the fileName pattern.
 * Convenience class used to create a {@link TextLogEventObserver}
 * with a {@link DateRollingFileDestination} and a suitable default
   {@link LogEventFormatter}.
 *
 * @author Johannes Brodwall
 */
public class DateRollingLogEventObserver extends TextLogEventObserver {

    public DateRollingLogEventObserver(String fileName, LogEventFormatter logEventFormatter) throws IOException {
        super(new DateRollingFileDestination(fileName), logEventFormatter);
    }

    public DateRollingLogEventObserver(String fileName) throws IOException {
        this(fileName, new TTLLEventLogFormatter());
    }

    public DateRollingLogEventObserver(Properties configuration, String prefix) throws IOException {
        super(new DateRollingFileDestination(configuration, prefix),
                createFormatter(configuration, prefix));
        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

    private static LogEventFormatter createFormatter(Properties configuration, String prefix) {
        if (configuration.containsKey(prefix + ".logEventFormatter")) {
            return ConfigUtil.create(prefix + ".logEventFormatter", "org.logevents.destinations", configuration);
        } else {
            return new TTLLEventLogFormatter();
        }
    }


}
