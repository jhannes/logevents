package org.logeventsdemo;

import org.logevents.LogEventFactory;
import org.logevents.core.LogEventFilter;
import org.slf4j.LoggerFactory;

import java.util.logging.Logger;

public class DemoJavaUtilLogging {

    private final static Logger someLog = Logger.getLogger("org.example.SomeDemoJavaUtilLogging");

    private final Logger log = Logger.getLogger("org.example.DemoJavaUtilLogging");

    public static void main(String[] args) {
        someLog.info("Log before logevent setup");

        LogEventFactory.getInstance().setFilter("org.example", new LogEventFilter(
                "INFO,WARN@marker=TODO,DEBUG@marker=URGENT"
        ));

        new DemoJavaUtilLogging().run();
    }

    private void run() {
        log.info("Info Testing");
        log.fine("Fine Testing");
    }
}
