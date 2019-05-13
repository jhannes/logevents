package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.util.Configuration;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Properties;

/**
 * Log events to a file with the date appended to the fileName pattern.
 *
 * @author Johannes Brodwall
 */
public class DateRollingLogEventObserver extends FileLogEventObserver {

    public DateRollingLogEventObserver(String fileName, LogEventFormatter formatter) {
        super(Optional.of(formatter), Optional.of(fileName));
    }

    public DateRollingLogEventObserver(String fileName) {
        this(fileName, new TTLLEventLogFormatter());
    }

    public DateRollingLogEventObserver(Configuration configuration) {
        this(configuration.getString("filename"),
                configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, TTLLEventLogFormatter.class));
        configuration.checkForUnknownFields();
    }

    @SuppressWarnings("unused")
    public DateRollingLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    @Override
    protected String getFilename(LogEvent logEvent) {
        return path.getFileName() + LocalDate.now().toString();
    }
}
