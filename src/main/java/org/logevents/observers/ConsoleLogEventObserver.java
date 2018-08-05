package org.logevents.observers;

import java.util.Properties;

import org.logevents.destinations.ConsoleLogEventDestination;
import org.logevents.destinations.ConsoleLogEventFormatter;
import org.logevents.destinations.LogEventFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ConfigUtil;

/**
 * Log messages to the system out with suitable formatter. Convenience class
 * that constructs a {@link TextLogEventObserver} with a reasonable default
 * destination (System.out) and format. By default, {@link ConsoleLogEventObserver}
 * will log with ANSI colors if supported (on Linux, Mac and when
 * <a href="https://github.com/fusesource/jansi">JANSI</a> is in the classpath on Windows).
 *
 * @author Johannes Brodwall
 */
public class ConsoleLogEventObserver extends TextLogEventObserver {

    public ConsoleLogEventObserver(LogEventFormatter logEventFormatter) {
        super(new ConsoleLogEventDestination(), logEventFormatter);
    }

    public ConsoleLogEventObserver() {
        this(new ConsoleLogEventFormatter());
    }

    public ConsoleLogEventObserver(Properties configuration, String prefix) {
        this(ConfigUtil.create(prefix + ".logEventFormatter", "org.logevents.destinations", configuration));
        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

}
