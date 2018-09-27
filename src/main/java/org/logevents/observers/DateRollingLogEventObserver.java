package org.logevents.observers;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;

/**
 * Log events to a file with the date appended to the fileName pattern.
 *
 * @author Johannes Brodwall
 */
public class DateRollingLogEventObserver extends FileLogEventObserver {

    public DateRollingLogEventObserver(String fileName, LogEventFormatter formatter) throws IOException {
        super(formatter, Paths.get(fileName));
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

    @Override
    protected String getFilename(LogEvent logEvent) {
        return path.getFileName() + LocalDate.now().toString();
    }
}
