package org.logevents.observers;

import java.net.MalformedURLException;
import java.util.Optional;
import java.util.Properties;

import org.logevents.observers.batch.SlackLogEventBatchProcessor;
import org.logevents.observers.batch.SlackLogEventsFormatter;
import org.logevents.util.Configuration;
import org.logevents.util.LogEventConfigurationException;
import org.slf4j.event.Level;

public class SlackLogEventObserver extends BatchingLogEventObserver {

    public SlackLogEventObserver(Properties properties, String prefix) {
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
                configuration.optionalString("channel")
        );
        SlackLogEventsFormatter formatter = configuration.createInstanceWithDefault("slackLogEventsFormatter", SlackLogEventsFormatter.class);
        formatter.setPackageFilter(configuration.getStringList("packageFilter"));

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
        slackLogEventBatchProcessor.setSlackLogEventsFormatter(formatter);


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
