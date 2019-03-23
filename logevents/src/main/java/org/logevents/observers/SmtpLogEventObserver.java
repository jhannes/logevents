package org.logevents.observers;

import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.smtp.SmtpLogEventBatchProcessor;
import org.logevents.util.Configuration;

import java.util.Properties;

/**
 * Writes log events as asynchronous batches as email over SMTP.
 * <p>
 * Example configuration:
 * <pre>
 * observer.email=SmtpLogEventObserver
 * observer.email.threshold=WARN
 * observer.email.cooldownTime=PT10S
 * observer.email.maximumWaitTime=PT5M
 * observer.email.idleThreshold=PT5S
 * observer.email.suppressMarkers=BORING_MARKER
 * observer.email.requireMarker=MY_MARKER, MY_OTHER_MARKER
 * observer.email.markers.MY_MARKER.throttle=PT1M PT10M PT30M
 * observer.email.fromAddress=alerts@example.com
 * observer.email.recipients=alerts@example.com
 * observer.email.applicationName=MY APP
 * observer.email.smtpUsername=userName
 * observer.email.password=secret password
 * observer.email.host=smtp.example.com
 * </pre>
 */
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
