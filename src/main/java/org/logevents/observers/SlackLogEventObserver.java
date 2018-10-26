package org.logevents.observers;

import java.net.MalformedURLException;
import java.util.Properties;

import org.logevents.LogEvent;
import org.logevents.observers.batch.SlackLogEventBatchProcessor;
import org.logevents.observers.batch.SlackLogEventsFormatter;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

public class SlackLogEventObserver extends BatchingLogEventObserver {

    private Level threshold;

    public SlackLogEventObserver(Properties properties, String prefix) throws MalformedURLException {
        this(new Configuration(properties, prefix));
    }

    public SlackLogEventObserver(Configuration configuration) {
        super(createBatchProcessor(configuration));

        threshold = configuration.optionalString("threshold").map(Level::valueOf).orElse(Level.DEBUG);
        idleThreshold = configuration.optionalDuration("idleThreshold").orElse(idleThreshold);
        cooldownTime = configuration.optionalDuration("cooldownTime").orElse(cooldownTime);
        maximumWaitTime = configuration.optionalDuration("maximumWaitTime").orElse(maximumWaitTime);

        configuration.checkForUnknownFields();
    }

    public static SlackLogEventBatchProcessor createBatchProcessor(Configuration configuration) {
        SlackLogEventBatchProcessor slackLogEventBatchProcessor = new SlackLogEventBatchProcessor(configuration.getUrl("slackUrl"));
        slackLogEventBatchProcessor.setSlackLogEventsFormatter(
                configuration.createInstanceWithDefault("slackLogEventsFormatter", SlackLogEventsFormatter.class));

        return slackLogEventBatchProcessor;
    }

    @Override
    public synchronized void logEvent(LogEvent event) {
        if (threshold.toInt() <= event.getLevel().toInt()) {
            super.logEvent(event);
        }
    }

}
