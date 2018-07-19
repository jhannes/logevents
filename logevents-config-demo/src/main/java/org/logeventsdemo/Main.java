package org.logeventsdemo;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        Logger logger = LoggerFactory.getLogger(Main.class);
        logger.debug("Hello world - debug", new IOException());
        logger.warn("Hello world - warning", new IOException());

        Thread.sleep(1 * 60 * 1000);
    }

}
