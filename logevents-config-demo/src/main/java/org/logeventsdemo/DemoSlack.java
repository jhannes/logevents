package org.logeventsdemo;

import org.logevents.DefaultLogEventConfigurator;
import org.logevents.LogEventFactory;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.SlackLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.Properties;

public class DemoSlack {

    public static void main(String[] args) throws InterruptedException {
        LogEventFactory factory = LogEventFactory.getInstance();

        // Get your webhook at https://www.slack.com/apps/manage/custom-integrations
        //  and put in logevents.properties as observers.slack.slackUrl=https://hooks.slack.com/services/XXXX/XXX/XXX
        DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();

        Properties properties = configurator.loadConfigurationProperties();
        properties.put("observer.slack", SlackLogEventObserver.class.getName());
        properties.put("observer.slack.threshold", Level.INFO.name());
        properties.put("observer.slack.cooldownTime", "PT5S");
        properties.put("observer.slack.maximumWaitTime", "PT3M");
        properties.put("observer.slack.idleThreshold", "PT3S");

        SlackLogEventObserver slackObserver = new SlackLogEventObserver(properties, "observer.slack");

        factory.setRootLevel(Level.INFO);
        factory.setRootObserver(CompositeLogEventObserver.combine(
                slackObserver,
                new ConsoleLogEventObserver()));

        MDC.put("User", System.getProperty("user.name"));

        Logger logger = factory.getLogger(DemoSlack.class.getName());
        logger.info("This message should not go to slack");

        logger.warn("This message should go to slack after 3 seconds idle delay");
        Thread.sleep(3500);

        logger.warn("This message about {} should be collected together", "one thing");
        logger.warn("This message about {} should be collected together", "another thing");
        logger.warn("This message about {} should be collected together", "something else");
        logger.error("Wait, what?", new IOException("Something went wrong"));
        Thread.sleep(5500); // Cooldown time from previous batch

        logger.error("Here is a message with an exception", new RuntimeException("Uh oh!"));
    }


}
