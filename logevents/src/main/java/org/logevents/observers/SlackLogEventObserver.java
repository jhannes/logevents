package org.logevents.observers;

import org.logevents.observers.batch.BatchThrottler;
import org.logevents.observers.batch.HttpPostJsonBatchProcessor;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.SlackLogEventsFormatter;
import org.logevents.util.Configuration;

import java.net.URL;
import java.util.Optional;
import java.util.Properties;

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
        SlackLogEventsFormatter formatter = configuration.createInstanceWithDefault("slackLogEventsFormatter", SlackLogEventsFormatter.class);
        formatter.setPackageFilter(configuration.getStringList("packageFilter"));
        formatter.setUsername(configuration.optionalString("username"));
        formatter.setChannel(configuration.optionalString("channel"));
        formatter.setShowRepeatsIndividually(configuration.getBoolean("showRepeatsIndividually"));
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
                new ExecutorScheduler(executor), createBatchProcessor(configuration, formatter))
                .setThrottle(throttle);
    }
}
