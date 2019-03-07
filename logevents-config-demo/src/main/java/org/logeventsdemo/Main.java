package org.logeventsdemo;

import org.logevents.DefaultLogEventConfigurator;
import org.logevents.LogEventFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class Main {

    private static Marker PERSONDATA = MarkerFactory.getMarker("PERSONDATA");


    public static void main(String[] args) throws InterruptedException {
        Logger logger = LoggerFactory.getLogger(Main.class);

        Properties configuration = new Properties();
        //configuration.setProperty("observer.console", "ConsoleLogEventObserver");
        configuration.setProperty("observer.console.formatter", "PatternLogEventFormatter");
        configuration.setProperty("observer.console.formatter.pattern", "%time [%mdc] [%thread] [%-5coloredLevel] [%bold(%logger)]: %msg");
        configuration.setProperty("observer.console.packageFilter", "org.logeventsdemo,java.io");
        configuration.setProperty("observer.console.threshold", "INFO");
        configuration.setProperty("observer.console.requireMarker", "PERSONDATA");
        //configuration.setProperty("root", "DEBUG console");

        new DefaultLogEventConfigurator().loadConfiguration(
                (LogEventFactory) LoggerFactory.getILoggerFactory(), configuration);


        try (MDC.MDCCloseable mdcCloseable = MDC.putCloseable("Context", "The context of the thread")) {
            logger.debug("Hello world - debug", new IOException(new RuntimeException()));
            logger.warn("Hello world - warning", new IOException());
            logger.error("A message without exception");

            Logger logger2 = LoggerFactory.getLogger("something-else");
            logger2.debug("Hello world - debug", new IOException(new RuntimeException()));
            logger2.warn("Hello world - warning", new IOException());
            logger2.error(PERSONDATA, "A message without exception - persondata");
            logger2.info("A message");

            try {
                new FileReader(new File("this/file/does/not/exist"));
            } catch (FileNotFoundException e) {
                logger2.error("Sometime went wrong ", e);
            }

            Thread.sleep(60 * 1000);
        }
    }

}
