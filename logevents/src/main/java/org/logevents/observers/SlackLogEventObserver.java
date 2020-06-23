package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.observers.batch.BatcherFactory;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatcherWithMdc;
import org.logevents.observers.batch.SlackLogEventsFormatter;
import org.logevents.observers.batch.ThrottlingBatcher;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

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
 * observer.slack.slackLogEventsFormatter={@link SlackLogEventsFormatter}
 * observer.slack.showRepeatsIndividually=false
 * observer.slack.channel=alertChannel
 * observer.slack.iconEmoji=:ant:
 * observer.slack.cooldownTime=PT10S
 * observer.slack.maximumWaitTime=PT5M
 * observer.slack.idleThreshold=PT5S
 * observer.slack.suppressMarkers=BORING_MARKER
 * observer.slack.requireMarker=MY_MARKER, MY_OTHER_MARKER
 * observer.slack.detailUrl=link to your {@link LogEventsServlet}
 * observer.slack.packageFilter=sun.www, com.example.uninteresting
 * observer.slack.includedMdcKeys=clientIp
 * observer.slack.markers.MY_MARKER.throttle=PT1M PT10M PT30M
 * observer.slack.markers.MY_MARKER.mdc=userId
 * observer.slack.markers.MY_MARKER.emoji=:rocket:
 * </pre>
 *
 * @see BatchingLogEventObserver
 * @see MicrosoftTeamsLogEventObserver
 * @see ThrottlingBatcher
 */
public class SlackLogEventObserver extends HttpPostJsonLogEventObserver {

    private final SlackLogEventsFormatter formatter;

    public SlackLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public SlackLogEventObserver(Configuration configuration) {
        super(configuration.optionalUrl("slackUrl").orElse(null));
        this.formatter = setupFormatter(configuration);

        configureFilter(configuration);
        configureBatching(configuration);
        configureMarkers(configuration);

        configuration.checkForUnknownFields();
    }

    public SlackLogEventObserver(URL slackUrl, Optional<String> username, Optional<String> channel) {
        this(slackUrl, new SlackLogEventsFormatter(username, channel));
    }

    public SlackLogEventObserver(URL slackUrl, SlackLogEventsFormatter formatter) {
        super(slackUrl);
        this.formatter = formatter;
    }

    protected SlackLogEventsFormatter setupFormatter(Configuration configuration) {
        SlackLogEventsFormatter formatter = createFormatter(configuration);
        formatter.setUsername(
                Optional.ofNullable(configuration.optionalString("username")
                        .orElseGet(configuration::getApplicationNode))
        );
        formatter.setPackageFilter(configuration.getPackageFilter());
        formatter.setIncludedMdcKeys(configuration.getIncludedMdcKeys());
        formatter.setChannel(configuration.optionalString("channel"));
        formatter.setIconEmoji(configuration.optionalString("iconEmoji"));
        formatter.setShowRepeatsIndividually(configuration.getBoolean("showRepeatsIndividually"));
        formatter.setDetailUrl(configuration.optionalString("detailUrl"));

        return formatter;
    }

    protected SlackLogEventsFormatter createFormatter(Configuration configuration) {
        return configuration.createInstanceWithDefault("formatter", SlackLogEventsFormatter.class);
    }

    @Override
    protected Consumer<List<LogEvent>> createProcessor(Configuration configuration, String markerName) {
        SlackLogEventsFormatter formatter = createMarkerFormatter(configuration, markerName);
        return batch -> sendBatch(new LogEventBatch(batch), formatter::createMessage);
    }

    protected SlackLogEventsFormatter createMarkerFormatter(Configuration configuration, String markerName) {
        SlackLogEventsFormatter formatter = setupFormatter(configuration);
        configuration.optionalString("markers." + markerName + ".username")
                .ifPresent(username -> formatter.setUsername(Optional.of(username)));
        configuration.optionalString("markers." + markerName + ".channel")
                .ifPresent(channel -> formatter.setChannel(Optional.of(channel)));
        configuration.optionalString("markers." + markerName + ".emoji")
                .ifPresent(emoji -> formatter.setIconEmoji(Optional.of(emoji)));
        return formatter;
    }

    @Override
    protected LogEventObserver createMdcBatcher(BatcherFactory batcherFactory, Configuration configuration, String markerName, String mdcKey) {
        SlackLogEventsFormatter formatter = createMarkerFormatter(configuration, markerName);
        Consumer<List<LogEvent>> processor = batch -> sendBatch(new LogEventBatch(batch), formatter::createMessage);

        return new LogEventBatcherWithMdc(batcherFactory, markerName, mdcKey, processor);
    }

    @Override
    protected Map<String, Object> formatBatch(LogEventBatch batch) {
        return formatter.createMessage(batch);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{formatter=" + formatter + ",url=" + getUrl() + '}';
    }

}
