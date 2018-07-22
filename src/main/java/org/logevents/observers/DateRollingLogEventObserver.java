package org.logevents.observers;

import java.io.IOException;
import java.util.Properties;

import org.logevents.destinations.DateRollingFileDestination;
import org.logevents.destinations.LogEventFormatter;
import org.logevents.util.ConfigUtil;

public class DateRollingLogEventObserver extends TextLogEventObserver {

    public DateRollingLogEventObserver(String fileName, LogEventFormatter logEventFormatter) throws IOException {
        super(new DateRollingFileDestination(fileName), logEventFormatter);
    }

    public DateRollingLogEventObserver(String fileName) throws IOException {
        this(fileName, LogEventFormatter.withDefaultFormat());
    }

    public DateRollingLogEventObserver(Properties configuration, String prefix) throws IOException {
        super(new DateRollingFileDestination(configuration, prefix),
                ConfigUtil.create(prefix + ".logEventFormatter", "org.logevents.destinations", configuration));
    }


}
