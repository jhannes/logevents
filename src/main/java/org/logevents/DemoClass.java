package org.logevents;

import java.io.IOException;

import org.logevents.extend.ansi.AnsiLogEventFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class DemoClass {

    public static void main(String[] args) throws IOException {
        LogEventConfigurator configurator = new LogEventConfigurator();
        configurator.setLevel(Level.WARN);
        configurator.setObserver(configurator.combine(
                LogEventConfigurator.consoleObserver(new AnsiLogEventFormatter()),
                configurator.dateRollingAppender("logs/application.log")
                ));

        Logger logger = LoggerFactory.getLogger(DemoClass.class);
        logger.warn("Hello to child {}: {}", "world", 123122);

        Logger parentLogger = LoggerFactory.getLogger("org.logevents");
        parentLogger.warn("Hello to parent {}: {}", "world", 123122);

        Logger rootLogger = LoggerFactory.getLogger("");
        rootLogger.warn("Hello to root {}: {}", "world", 123122);
    }

}
