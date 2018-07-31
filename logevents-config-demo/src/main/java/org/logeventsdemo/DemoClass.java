package org.logeventsdemo;

import java.io.IOException;

import org.logevents.LogEventFactory;
import org.logevents.observers.DateRollingLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class DemoClass {

    public static void main(String[] args) throws IOException {
        LogEventFactory logEventFactory = LogEventFactory.getInstance();

        logEventFactory.setRootLevel(Level.ERROR);
        logEventFactory.addRootObserver(new DateRollingLogEventObserver("target/logs/application.log"));

        logEventFactory.setLevel("org.logevents", Level.INFO);
        logEventFactory.addObserver("org.logevents", new DateRollingLogEventObserver("target/logs/info.log"));

        Logger logger = LoggerFactory.getLogger("org.logevents.DemoClass");
        logger.warn("Hello to child {}: {}", "world", 123122);

        Logger parentLogger = LoggerFactory.getLogger("org.logevents");
        parentLogger.warn("Hello to parent {}: {}", "world", 123122);

        Logger rootLogger = LoggerFactory.getLogger("");
        rootLogger.info("Hello to root {}: {}", "world", 123122);

        rootLogger.error("Here is an error");
    }

}
