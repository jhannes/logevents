package org.logeventsdemo;

import org.logevents.DefaultLogEventConfigurator;
import org.logevents.LogEventFactory;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.MicrosoftTeamsLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

public class DemoTeams {

    public static void main(String[] args) throws InterruptedException {
        LogEventFactory factory = LogEventFactory.getInstance();

        // Get yours webhook at https://docs.microsoft.com/en-us/microsoftteams/platform/concepts/connectors/connectors-using
        //  and put in logevents.properties as observers.teams.url=https://outlook.office.com/webhook/.../IncomingWebHook/...

        DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
        Properties properties = configurator.configureLogEventFactory();

        MicrosoftTeamsLogEventObserver teamsObserver = new MicrosoftTeamsLogEventObserver(
                properties, "observer.teams"
        );
        teamsObserver.setThreshold(Level.INFO);
        teamsObserver.setCooldownTime(Duration.ofSeconds(5));
        teamsObserver.setMaximumWaitTime(Duration.ofMinutes(3));
        teamsObserver.setIdleThreshold(Duration.ofSeconds(3));

        factory.setRootLevel(Level.INFO);
        factory.setRootObserver(CompositeLogEventObserver.combine(
                teamsObserver,
                new ConsoleLogEventObserver()));

        MDC.put("User", System.getProperty("user.name"));

        Logger logger = factory.getLogger(DemoTeams.class.getName());
        logger.info("This message should not go to Teams");

        logger.warn("This message should go to teams after 3 seconds idle delay");
        Thread.sleep(3500);

        logger.warn("This message about {} should be collected together", "one thing");
        logger.warn("This message about {} should be collected together", "another thing");
        logger.warn("This message about {} should be collected together", "something else");
        logger.error("Wait, what?", new IOException("Something went wrong"));
        Thread.sleep(5500); // Cooldown time from previous batch

        logger.error("Here is a message with an exception", new RuntimeException("Uh oh!"));
        Thread.sleep(5500); // Cooldown time from previous batch
    }


}
