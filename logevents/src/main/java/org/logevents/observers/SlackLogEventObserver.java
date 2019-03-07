package org.logevents.observers;

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
        super(createBatchProcessor(configuration));

        configureFilter(configuration);
        configureBatching(configuration);

        configuration.checkForUnknownFields();
    }

    public SlackLogEventObserver(URL slackUrl, Optional<String> username, Optional<String> channel) {
        super(new HttpPostJsonBatchProcessor(slackUrl, new SlackLogEventsFormatter(username, channel)));
    }

    private static LogEventBatchProcessor createBatchProcessor(Configuration configuration) {
        return new HttpPostJsonBatchProcessor(
                configuration.optionalUrl("slackUrl").orElse(null),
                createFormatter(configuration)
        );
    }

    private static SlackLogEventsFormatter createFormatter(Configuration configuration) {
        SlackLogEventsFormatter formatter = configuration.createInstanceWithDefault("slackLogEventsFormatter", SlackLogEventsFormatter.class);
        formatter.setPackageFilter(configuration.getStringList("packageFilter"));
        formatter.setUsername(configuration.optionalString("username"));
        formatter.setChannel(configuration.optionalString("channel"));
        formatter.configureSourceCode(configuration);

        return formatter;
    }

    @Override
    public HttpPostJsonBatchProcessor getBatchProcessor() {
        return (HttpPostJsonBatchProcessor) super.getBatchProcessor();
    }
}
