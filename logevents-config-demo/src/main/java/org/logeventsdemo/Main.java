package org.logeventsdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.NoRouteToHostException;

public class Main {

    private static Marker PERSONDATA = MarkerFactory.getMarker("PERSONDATA");
    private static Marker SECURITY = MarkerFactory.getMarker("SECURITY");
    private static Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    static {
        PERSONDATA.add(AUDIT);
        SECURITY.add(AUDIT);
    }



    public static void main(String[] args) throws InterruptedException {
        Logger logger = LoggerFactory.getLogger(Main.class);

        try (MDC.MDCCloseable ignored = MDC.putCloseable("Context", "The context of the thread")) {
            logger.info("While connecting to database", new NoRouteToHostException("Connection refused"));
            logger.warn("Database failure with error message: {}", "AA39111");

            Logger logger2 = LoggerFactory.getLogger("com.example.myapp.DatabaseActionController");
            logger2.info("Action={} caseNumber={}", "Delete case", 2311);
            logger2.error(AUDIT, "Unauthorized action: user={} action={}", "Alice", "Delete case", new IOException("Network Failure"));
            Thread.sleep(100);
            logger2.info("Action={} caseNumber={}", "Remove logs", 2311);
            logger2.error(AUDIT, "Unauthorized action: user={} action={}", "Alice", "Remove logs");
            Thread.sleep(100);
            logger2.info("Action={} caseNumber={}", "Remove logs", 2311);
            logger2.error(AUDIT, "Unauthorized action: user={} action={}", "Alice", "Remove logs");
            Thread.sleep(100);

            try {
                new FileReader(new File("this/file/does/not/exist"));
            } catch (FileNotFoundException e) {
                logger2.error("Something went wrong ", e);
            }
        } finally {
            Thread.sleep(14 * 1000);
        }
    }

}
