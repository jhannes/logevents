package org.logevents.demo.smtp;

import org.logevents.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class SmtpLogeventsDemo {

    private static Logger logger = LoggerFactory.getLogger(SmtpLogeventsDemo.class);

    public static void main(String[] args) throws InterruptedException {
        LogEvent.class.getName();
        try (MDC.MDCCloseable request = MDC.putCloseable("REQUEST", "http://localhost/foo")) {
            logger.info("Something started");
            logger.debug("User did {}", "one thing");
            logger.debug("User did {}", "another thing");
            logger.error("Something went wrong!");
            logger.debug("User did {}", "yet another thing");
            logger.debug("We tried to clean up but failed");
        }

        try (MDC.MDCCloseable request = MDC.putCloseable("REQUEST", "http://localhost/bar")) {
            logger.info("Something started");
            logger.error("Something went wrong!");
            logger.debug("User did {}", "yet another thing");
        }

        try (MDC.MDCCloseable request = MDC.putCloseable("REQUEST", "http://localhost/ok")) {
            logger.info("Something started");
            logger.debug("User did {}", "yet another thing");
        }

        // Give the LogEventBatchObserver time to cool down
        Thread.sleep(60000L);
    }
}
