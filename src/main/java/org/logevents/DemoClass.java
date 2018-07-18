package org.logevents;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoClass {

    public static void main(String[] args) throws IOException {
        Logger logger = LoggerFactory.getLogger(DemoClass.class);
        logger.warn("Hello to child {}: {}", "world", 123122);

        Logger parentLogger = LoggerFactory.getLogger("org.logevents");
        parentLogger.warn("Hello to parent {}: {}", "world", 123122);

        Logger rootLogger = LoggerFactory.getLogger("");
        rootLogger.warn("Hello to root {}: {}", "world", 123122);
    }

}
