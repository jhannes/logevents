package org.logevents.observers;

import org.logevents.config.Configuration;
import org.logevents.observers.batch.BatchThrottler;
import org.logevents.observers.batch.ExecutorScheduler;
import org.logevents.observers.batch.HttpPostJsonBatchProcessor;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.SlackLogEventsFormatter;

import java.net.URL;
import java.util.Optional;
import java.util.Properties;

/**
 * Writes log events as asynchronous batches to Slack. Slack is a great destination for logging as it
 * provides great notification support on mobile and desktop platforms. Requires
 * a Slack Web Hook, which you can create as a <a href="https://www.slack.com/apps/manage/custom-integrations">
 * Slack Custom Integration</a>.
 * <p>
 * Example configuration:
 * <pre>
 * observer.slack=SlackLogEventObserver
 * observer.slack.slackUrl=https://hooks.slack.com/services/XXXX/XXX/XXX
 * observer.slack.threshold=WARN
 * observer.slack.slackLogEventsFormatter={@link org.logevents.observers.batch.SlackLogEventsFormatter}
 * observer.slack.showRepeatsIndividually=false
 * observer.slack.channel=alertChannel
 * observer.slack.cooldownTime=PT10S
 * observer.slack.maximumWaitTime=PT5M
 * observer.slack.idleThreshold=PT5S
 * observer.slack.suppressMarkers=BORING_MARKER
 * observer.slack.requireMarker=MY_MARKER, MY_OTHER_MARKER
 * observer.slack.markers.MY_MARKER.throttle=PT1M PT10M PT30M
 * observer.slack.detailUrl=link to your {@link org.logevents.extend.servlets.LogEventsServlet}
 * </pre>
 *
 * @see BatchingLogEventObserver
 * @see MicrosoftTeamsLogEventObserver
 * @see BatchThrottler
 */
public class SlackLogEventObserver extends BatchingLogEventObserver {

    public SlackLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public SlackLogEventObserver(Configuration configuration) {
        super(createBatchProcessor(configuration, createFormatter(configuration)));

        configureFilter(configuration);
        configureBatching(configuration);

        configureMarkers(configuration);

        configuration.checkForUnknownFields();
    }

    public SlackLogEventObserver(URL slackUrl, Optional<String> username, Optional<String> channel) {
        super(new HttpPostJsonBatchProcessor(slackUrl, new SlackLogEventsFormatter(username, channel)));
    }

    private static LogEventBatchProcessor createBatchProcessor(Configuration configuration, SlackLogEventsFormatter formatter) {
        return new HttpPostJsonBatchProcessor(
                configuration.optionalUrl("slackUrl").orElse(null),
                formatter
        );
    }

    private static SlackLogEventsFormatter createFormatter(Configuration configuration) {
        SlackLogEventsFormatter formatter = configuration.createInstanceWithDefault("formatter", SlackLogEventsFormatter.class);
        String[] packageFilters = configuration.getStringList("packageFilter");
        if (packageFilters.length == 0) {
            packageFilters = configuration.getDefaultStringList("packageFilter");
        }
        formatter.setPackageFilter(packageFilters);
        formatter.setUsername(configuration.optionalString("username"));
        formatter.setChannel(configuration.optionalString("channel"));
        formatter.setShowRepeatsIndividually(configuration.getBoolean("showRepeatsIndividually"));
        formatter.setDetailUrl(configuration.optionalString("detailUrl"));
        formatter.configureSourceCode(configuration);

        return formatter;
    }

    @Override
    protected BatchThrottler createBatcher(Configuration configuration, String markerName) {
        SlackLogEventsFormatter formatter = createFormatter(configuration);
        configuration.optionalString("markers." + markerName + ".channel")
                .ifPresent(channel -> formatter.setChannel(Optional.of(channel)));
        String throttle = configuration.getString("markers." + markerName + ".throttle");
        return new BatchThrottler(
                new ExecutorScheduler(scheduledExecutorService, shutdownHook), createBatchProcessor(configuration, formatter))
                .setThrottle(throttle);
    }
}
