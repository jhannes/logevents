package org.logevents.demo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import org.logevents.LogEventConfigurator;
import org.logevents.extend.ansi.AnsiLogEventFormatter;
import org.logevents.observers.batch.BatchingLogEventObserver;
import org.logevents.observers.batch.SlackLogEventBatchProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

public class DemoSlack {

    public static void main(String[] args) throws InterruptedException, MalformedURLException {
        LogEventConfigurator configurator = new LogEventConfigurator();
        configurator.setLevel(Level.INFO);

        SlackLogEventBatchProcessor slackLogEventBatchProcessor = new SlackLogEventBatchProcessor();
        slackLogEventBatchProcessor.setUsername("Loge Vents");
        slackLogEventBatchProcessor.setChannel("test");
        slackLogEventBatchProcessor.setSlackUrl(new URL("https://tet"));


        BatchingLogEventObserver batchEventObserver = configurator.batchEventObserver(slackLogEventBatchProcessor);
        batchEventObserver.setCooldownTime(Duration.ofSeconds(5));
        batchEventObserver.setMaximumWaitTime(Duration.ofMinutes(3));
        batchEventObserver.setIdleThreshold(Duration.ofSeconds(3));

        configurator.setObserver(configurator.combine(
                LogEventConfigurator.levelThresholdObserver(Level.WARN, batchEventObserver),
                LogEventConfigurator.consoleObserver(new AnsiLogEventFormatter())));


        MDC.put("User", System.getProperty("user.name"));

        Logger logger = LoggerFactory.getLogger(DemoSlack.class);
        logger.info("This message should not go to slack");

        logger.warn("This message should go to slack after 3 seconds idle delay");
        Thread.sleep(3500);

        logger.warn("This message about {} should be collected together", "one thing");
        logger.warn("This message about {} should be collected together", "another thing");
        logger.warn("This message about {} should be collected together", "something else");
        logger.warn("Wait, what?", new IOException());
        Thread.sleep(5500); // Cooldown time from previous batch

        logger.error("Here is a message with an exception", new RuntimeException());
        Thread.sleep(5500); // Cooldown time from previous batch


        Thread.sleep(3600000);
    }


}
