package org.logevents.observers;

import org.logevents.observers.batch.HttpPostJsonBatchProcessor;
import org.logevents.observers.batch.MicrosoftTeamsMessageFormatter;
import org.logevents.util.Configuration;

import java.util.Properties;

/**
 * Sends log messages to Microsoft Teams through a webhook extension. Must be configured
 * with a <code>.url</code> property as a webhook. See <a href="https://docs.microsoft.com/en-us/microsoftteams/platform/concepts/connectors/connectors-using"
 * >Microsoft documentation</a> on how to get a Webhook url.
 * This observer batches messages as standard for {@link BatchingLogEventObserver},
 * using the <code>idleThreshold</code>, <code>idleThreshold</code>, and <code>cooldownTime</code> to
 * determine when to flush the batch. It support {@link FilteredLogEventObserver} properties
 * <code>threshold</code>, <code>suppressMarkers</code> and <code>requireMarker</code> to filter sent messages
 *
 * <p><strong>State: Preview.</strong></p>
 */
public class MicrosoftTeamsLogEventObserver extends BatchingLogEventObserver {

    public MicrosoftTeamsLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public MicrosoftTeamsLogEventObserver(Configuration configuration) {
        super(new HttpPostJsonBatchProcessor(
                configuration.getUrl("url"),
                configuration.createInstanceWithDefault("formatter", MicrosoftTeamsMessageFormatter.class)
        ));
        configureFilter(configuration);
        configureBatching(configuration);

        configuration.checkForUnknownFields();
    }
}
