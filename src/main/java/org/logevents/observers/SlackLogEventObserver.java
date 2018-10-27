package org.logevents.observers;

import java.net.MalformedURLException;
import java.util.Properties;

import org.logevents.observers.batch.SlackLogEventBatchProcessor;
import org.logevents.observers.batch.SlackLogEventsFormatter;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

public class SlackLogEventObserver extends BatchingLogEventObserver {

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

    private static SlackLogEventBatchProcessor createBatchProcessor(Configuration configuration) {
        SlackLogEventBatchProcessor slackLogEventBatchProcessor = new SlackLogEventBatchProcessor(
                configuration.getUrl("slackUrl"),
                configuration.optionalString("username"),
                configuration.optionalString("channel"));
        slackLogEventBatchProcessor.setSlackLogEventsFormatter(
                configuration.createInstanceWithDefault("slackLogEventsFormatter", SlackLogEventsFormatter.class));

        return slackLogEventBatchProcessor;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + "username=" + getProcessor().getUsername().orElse("") + ","
                + "channel=" + getProcessor().getChannel().orElse("") + ","
                + "slackUrl=" + getProcessor().getSlackUrl()
                + "}";
    }

    private SlackLogEventBatchProcessor getProcessor() {
        return (SlackLogEventBatchProcessor) batchProcessor;
    }
}
