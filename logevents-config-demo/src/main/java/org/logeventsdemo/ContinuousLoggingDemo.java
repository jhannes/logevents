package org.logeventsdemo;

import org.logevents.LogEventFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContinuousLoggingDemo {

    private static final Logger logger = LoggerFactory.getLogger(ContinuousLoggingDemo.class);


    public static void main(String[] args) throws InterruptedException {
        System.out.println(((LogEventFactory)LoggerFactory.getILoggerFactory()).getRootLogger());
        System.out.println(((LogEventFactory)LoggerFactory.getILoggerFactory()).getLoggers());
        while (true) {
            logger.debug("Here is a debug message");

            Thread.sleep(100L);

            logger.trace("Here is a trace message");
            logger.warn("Here is a warning");

            Thread.sleep(3_000L);
        }
    }

}
