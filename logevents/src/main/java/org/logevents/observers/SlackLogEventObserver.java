package org.logevents.observers;

import org.logevents.observers.batch.HttpPostJsonBatchProcessor;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.SlackLogEventsFormatter;
import org.logevents.util.Configuration;
import org.logevents.util.LogEventConfigurationException;

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

        configuration.optionalString("sourceCode");

        int index = 1;
        Optional<String> sourcePackages;
        while ((sourcePackages = configuration.optionalString("sourceCode." + index + ".package")).isPresent()) {
            Optional<String> githubLocation = configuration.optionalString("sourceCode." + index + ".github");
            Optional<String> mavenLocation = configuration.optionalString("sourceCode." + index + ".maven");
            Optional<String> bitbucketLocation = configuration.optionalString("sourceCode." + index + ".bitbucket");

            if (githubLocation.isPresent()) {
                formatter.addPackageGithubLocation(sourcePackages.get(), githubLocation.get(), configuration.optionalString("sourceCode." + index + ".tag"));
            } else if (bitbucketLocation.isPresent()) {
                formatter.addPackageBitbucket5Location(sourcePackages.get(), bitbucketLocation.get(), configuration.optionalString("sourceCode." + index + ".tag"));
            } else if (mavenLocation.isPresent()) {
                formatter.addPackageMavenLocation(sourcePackages.get(), mavenLocation.get());
            } else {
                throw new LogEventConfigurationException("Can't find source code location for " + sourcePackages);
            }
            index++;
        }

        return formatter;
    }

    @Override
    public HttpPostJsonBatchProcessor getBatchProcessor() {
        return (HttpPostJsonBatchProcessor) super.getBatchProcessor();
    }
}
