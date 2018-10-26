package org.logeventsdemo;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        Logger logger = LoggerFactory.getLogger(Main.class);
        logger.debug("Hello world - debug", new IOException(new RuntimeException()));
        logger.warn("Hello world - warning", new IOException());
        logger.error("A message without exception");

        Logger logger2 = LoggerFactory.getLogger("something-else");
        logger2.debug("Hello world - debug", new IOException(new RuntimeException()));
        logger2.warn("Hello world - warning", new IOException());
        logger2.error("A message without exception");

        Thread.sleep(1 * 60 * 1000);
    }

}
