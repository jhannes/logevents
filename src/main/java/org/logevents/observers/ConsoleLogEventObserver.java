package org.logevents.observers;

import java.util.Properties;

import org.logevents.destinations.ConsoleLogEventDestination;
import org.logevents.destinations.ConsoleLogEventFormatter;
import org.logevents.destinations.LogEventFormatter;
import org.logevents.util.ConfigUtil;

public class ConsoleLogEventObserver extends TextLogEventObserver {

    public ConsoleLogEventObserver(LogEventFormatter logEventFormatter) {
        super(new ConsoleLogEventDestination(), logEventFormatter);
    }

    public ConsoleLogEventObserver() {
        this(new ConsoleLogEventFormatter());
    }

    public ConsoleLogEventObserver(Properties configuration, String prefix) {
        this(ConfigUtil.create(prefix + ".logEventFormatter", "org.logevents.destinations", configuration));
    }

}
