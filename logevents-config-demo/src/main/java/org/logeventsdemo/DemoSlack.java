package org.logeventsdemo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import org.logevents.LogEventFactory;
import org.logevents.observers.BatchingLogEventObserver;
import org.logevents.observers.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.LevelThresholdConditionalObserver;
import org.logevents.observers.batch.SlackLogEventBatchProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

public class DemoSlack {

    public static void main(String[] args) throws InterruptedException, MalformedURLException {
        LogEventFactory factory = LogEventFactory.getInstance();

        factory.setRootLevel(Level.INFO);

        // Get yours at https://www.slack.com/apps/manage/custom-integrations
        URL slackUrl = new URL("https://hooks.slack.com/services/....");
        SlackLogEventBatchProcessor slackLogEventBatchProcessor = new SlackLogEventBatchProcessor(slackUrl);
        slackLogEventBatchProcessor.setUsername("Loge Vents");
        slackLogEventBatchProcessor.setChannel("test");

        BatchingLogEventObserver batchEventObserver = new BatchingLogEventObserver(slackLogEventBatchProcessor);
        batchEventObserver.setCooldownTime(Duration.ofSeconds(5));
        batchEventObserver.setMaximumWaitTime(Duration.ofMinutes(3));
        batchEventObserver.setIdleThreshold(Duration.ofSeconds(3));
        factory.setRootObserver(CompositeLogEventObserver.combine(
                new LevelThresholdConditionalObserver(Level.WARN, batchEventObserver),
                new ConsoleLogEventObserver()));

        MDC.put("User", System.getProperty("user.name"));

        Logger logger = LoggerFactory.getLogger(DemoSlack.class);
        logger.info("This message should not go to slack");

        logger.warn("This message should go to slack after 3 seconds idle delay");
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
