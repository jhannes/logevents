package org.logevents.observers;

import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.MicrosoftTeamsMessageFormatter;
import org.logevents.observers.batch.SlackLogEventsFormatter;
import org.logevents.observers.batch.ThrottlingBatcher;

import java.util.Map;
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
 * <p>
 * Example configuration:
 * <pre>
 * observer.teams=SlackLogEventObserver
 * observer.teams.url=https://outlook.office.com/webhook/.../IncomingWebHook/...
 * observer.teams.format.detailUrl=link to your {@link LogEventsServlet}
 * observer.teams.threshold=WARN
 * observer.teams.suppressMarkers=BORING_MARKER
 * observer.teams.requireMarker=MY_MARKER, MY_OTHER_MARKER
 * observer.teams.cooldownTime=PT10S
 * observer.teams.maximumWaitTime=PT5M
 * observer.teams.idleThreshold=PT5S
 * observer.teams.includedMdcKeys=clientIp
 * observer.teams.markers.MY_MARKER.throttle=PT1M PT10M PT30M
 * observer.teams.markers.MY_MARKER.mdc=userId
 * observer.teams.proxy=proxy.example.net:8888
 * </pre>
 *
 * <h2>Implementation notes</h2>
 *
 * <ul>
 *     <li>Unfortunately, the ability to mention &#064;channel is still
 *      <a href="https://microsoftteams.uservoice.com/forums/555103-public/suggestions/17153099-webhook-needs-to-support-forced-notification-a-la">Unavailable in Teams</a></li>
 * </ul>
 *
 * @see BatchingLogEventObserver
 * @see SlackLogEventsFormatter
 * @see ThrottlingBatcher
 */
public class MicrosoftTeamsLogEventObserver extends HttpPostJsonLogEventObserver {

    private final MicrosoftTeamsMessageFormatter formatter;

    public MicrosoftTeamsLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public MicrosoftTeamsLogEventObserver(Configuration configuration) {
        super(configuration.getUrl("url"));
        configureFilter(configuration);
        configureBatching(configuration);
        configureMarkers(configuration);
        configureProxy(configuration);

        this.formatter = configuration.createInstanceWithDefault("formatter", MicrosoftTeamsMessageFormatter.class);
        formatter.setIncludedMdcKeys(configuration.getIncludedMdcKeys());
        formatter.setPackageFilter(configuration.getPackageFilter());

        configuration.checkForUnknownFields();
    }

    @Override
    protected Map<String, Object> formatBatch(LogEventBatch batch) {
        return formatter.createMessage(batch);
    }
}
