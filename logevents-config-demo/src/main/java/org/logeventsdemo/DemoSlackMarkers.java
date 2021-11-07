package org.logeventsdemo;

import org.logevents.LogEventFactory;
import org.logevents.config.DefaultLogEventConfigurator;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.SlackLogEventObserver;
import org.logevents.status.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.util.Map;

public class DemoSlackMarkers {

    private static final Marker SECURITY = MarkerFactory.getMarker("SECURITY");
    private static final Marker LOGIN = MarkerFactory.getMarker("LOGIN");
    private static final Marker SERVER = MarkerFactory.getMarker("SERVER");

    static {
        SECURITY.add(LOGIN);
    }


    public static void main(String[] args) throws InterruptedException {
        System.setProperty("logevents.status.ThrottlingBatcher", StatusEvent.StatusLevel.TRACE.name());
        System.setProperty("logevents.status.LogEventShutdownHook", StatusEvent.StatusLevel.TRACE.name());
        LogEventFactory factory = LogEventFactory.getInstance();

        // Get your webhook at https://www.slack.com/apps/manage/custom-integrations
        //  and put in logevents.properties as observers.slack.slackUrl=https://hooks.slack.com/services/XXXX/XXX/XXX

        DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
        Map<String, String> properties = configurator.loadConfigurationProperties();
        properties.put("observer.slack.threshold", "INFO");
        //properties.put("observer.slack.showRepeatsIndividually", "true");
        properties.put("observer.slack.markers.LOGIN.throttle", "PT3S PT5S");
        properties.put("observer.slack.markers.LOGIN.mdc", "userId");
        properties.put("observer.slack.markers.SECURITY.throttle", "PT3S PT5S PT10S");
        properties.put("observer.slack.markers.SECURITY.channel", "logging-test-2");

        SlackLogEventObserver slackObserver = new SlackLogEventObserver(properties, "observer.slack");
        factory.setRootLevel(Level.INFO);
        factory.setRootObserver(CompositeLogEventObserver.combine(
                slackObserver,
                new ConsoleLogEventObserver()));


        Logger logger = factory.getLogger(DemoSlackMarkers.class.getName());


        logger.warn("Warning without marker");
        Thread.sleep(500);
        logger.warn(SECURITY, "Warning with marker +{}", 0.5);
        Thread.sleep(500);
        logger.warn(SECURITY, "Warning {} with marker +{}s", 2, 1);
        Thread.sleep(1000);
        logger.warn(SECURITY, "Warning {} with marker +{}s", 3, 2);
        Thread.sleep(1000);
        MDC.put("userId", "alice");
        logger.warn(LOGIN, "User {} logged in from {}", "alice",  "127.0.0.1");
        Thread.sleep(100);
        logger.warn(LOGIN, "User {} logged in from {}", "alice",  "127.0.0.1");
        Thread.sleep(100);
        MDC.put("userId", "bob");
        logger.warn(LOGIN, "User {} logged in from {}", "bob",  "127.0.0.1");
        Thread.sleep(100);
        logger.warn(LOGIN, "User {} logged in from {}", "bob",  "127.0.0.1");
        Thread.sleep(100);
        logger.warn(LOGIN, "User {} logged in from {}", "bob",  "127.0.0.1");
        Thread.sleep(100);
        logger.warn(LOGIN, "User {} logged in from {}", "bob",  "127.0.0.1");
        Thread.sleep(100);
        logger.warn(LOGIN, "User {} logged in from {}", "bob",  "127.0.0.1");
        MDC.put("userId", "alice");
        logger.warn(LOGIN, "User {} logged in from {}", "alice",  "127.0.0.1");
        Thread.sleep(10);
        logger.info(SECURITY, "Warning {} with sub marker +{}s", 5, 4);
        Thread.sleep(2000);
        logger.info(SECURITY, "Warning {} with sub marker +{}s", 6, 6);
        Thread.sleep(2000);

        for (int i=0; i<10; i++) {
            logger.warn(SECURITY, "Warning {} with marker +{}s", 5+i, 6 + 2*i);
            Thread.sleep(2000);
        }

        Thread.sleep(10000);
    }
}
