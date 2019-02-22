package org.logeventsdemo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.logevents.DefaultLogEventConfigurator;
import org.logevents.LogEventFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        Logger logger = LoggerFactory.getLogger(Main.class);

        Properties configuration = new Properties();
        configuration.setProperty("observer.console", "ConsoleLogEventObserver");
        configuration.setProperty("observer.console.packageFilter", "org.logeventsdemo,java.io");

        new DefaultLogEventConfigurator().loadConfiguration(
                (LogEventFactory) LoggerFactory.getILoggerFactory(), configuration);


        logger.debug("Hello world - debug", new IOException(new RuntimeException()));
        logger.warn("Hello world - warning", new IOException());
        logger.error("A message without exception");

        Logger logger2 = LoggerFactory.getLogger("something-else");
        logger2.debug("Hello world - debug", new IOException(new RuntimeException()));
        logger2.warn("Hello world - warning", new IOException());
        logger2.error("A message without exception");

        try {
            new FileReader(new File("this/file/does/not/exist"));
        } catch (FileNotFoundException e) {
            logger2.error("Sometime went wrong ", e);
        }

        Thread.sleep(1 * 60 * 1000);
    }

}
