package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Properties;

/**
 * Log events to a file with the date appended to the fileName pattern.
 *
 * @author Johannes Brodwall
 */
public class DateRollingLogEventObserver extends FileLogEventObserver {

    private final String filename;

    public DateRollingLogEventObserver(String filename, LogEventFormatter formatter) {
        super(new Configuration(new Properties(), ""), filename, Optional.of(formatter));
        this.filename = filename;
    }

    public DateRollingLogEventObserver(String fileName) {
        this(fileName, new TTLLEventLogFormatter());
    }

    public DateRollingLogEventObserver(Configuration configuration) {
        super(
                configuration,
                configuration.getString("filename"),
                Optional.of(configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, TTLLEventLogFormatter.class))
        );
        this.filename = configuration.getString("filename");
        configuration.checkForUnknownFields();
    }

    @SuppressWarnings("unused")
    public DateRollingLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    @Override
    protected Path getFilename(LogEvent logEvent) {
        return Paths.get(filename + LocalDate.now().toString());
    }
}
