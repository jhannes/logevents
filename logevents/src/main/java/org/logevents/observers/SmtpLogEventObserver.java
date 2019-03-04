package org.logevents.observers;

import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.smtp.SmtpLogEventBatchProcessor;
import org.logevents.util.Configuration;

import java.util.Properties;

public class SmtpLogEventObserver extends BatchingLogEventObserver {
    public SmtpLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public SmtpLogEventObserver(Configuration configuration) {
        super(createBatchProcessor(configuration));

        configureFilter(configuration);
        idleThreshold = configuration.optionalDuration("idleThreshold").orElse(idleThreshold);
        cooldownTime = configuration.optionalDuration("cooldownTime").orElse(cooldownTime);
        maximumWaitTime = configuration.optionalDuration("maximumWaitTime").orElse(maximumWaitTime);

        configuration.checkForUnknownFields();
    }

    private static LogEventBatchProcessor createBatchProcessor(Configuration configuration) {
        return new SmtpLogEventBatchProcessor(configuration);
    }
}
