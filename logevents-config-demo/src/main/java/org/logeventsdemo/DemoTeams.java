package org.logeventsdemo;

import org.logevents.LogEventFactory;
import org.logevents.config.Configuration;
import org.logevents.config.DefaultLogEventConfigurator;
import org.logevents.core.CompositeLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.logevents.observers.MicrosoftTeamsLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class DemoTeams {

    public static void main(String[] args) throws InterruptedException {
        LogEventFactory factory = LogEventFactory.getInstance();

        // Get yours webhook at https://docs.microsoft.com/en-us/microsoftteams/platform/concepts/connectors/connectors-using
        //  and put in logevents.properties as observer.teams.url=https://outlook.office.com/webhook/.../IncomingWebHook/...
        //  or run with environement variable LOGEVENTS_OBSERVER_TEAMS_URL=https://outlook.office.com/webhook/.../IncomingWebHook/...

        DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
        Map<String, String> properties = configurator.loadConfigurationProperties();
        properties.put("observer.teams.idleThreshold", Duration.ofSeconds(1).toString());
        properties.put("observer.teams.cooldownTime", Duration.ofSeconds(2).toString());
        properties.put("observer.teams.maximumWaitTime", Duration.ofMinutes(3).toString());
        properties.put("observer.teams.formatter.detailUrl", "http://www.google.com");
        properties.put("observer.teams.formatter.exceptionFormatter.frameClassLength", "20");

        MicrosoftTeamsLogEventObserver teamsObserver = new MicrosoftTeamsLogEventObserver(
                properties, "observer.teams"
        );
        teamsObserver.setThreshold(Level.INFO);

        factory.setRootLevel(Level.INFO);
        factory.setRootObserver(CompositeLogEventObserver.combine(
                teamsObserver,
                new ConsoleLogEventObserver(new Configuration())));

        Logger logger = factory.getLogger(DemoTeams.class.getName());
        //logger.warn("Here is a message");
        //Thread.sleep(2500); // Cooldown time from previous batch

        MDC.put("User", System.getProperty("user.name"));

        logger.error("Here is a message with an exception about some_database_table", complexException());
        Thread.sleep(2500); // Cooldown time from previous batch

        logger.error("Here is a message with an exception", new RuntimeException("Uh oh!"));
        Thread.sleep(2500); // Cooldown time from previous batch

        logger.warn("This message about {} should be collected together", "one thing");
        logger.warn("This message about {} should be collected together", "another thing");
        logger.warn("This message about {} should be collected together", "something else");
        Thread.sleep(2500); // Cooldown time from previous batch

        MDC.put("Request", "https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=");
        logger.warn("This message about {} should be collected together", "one thing");
        logger.warn("This message about {} should be collected together", "another thing");
        logger.warn("This message about {} should be collected together", "something else");
        logger.error("Wait, what?", new IOException("Something went wrong"));
        Thread.sleep(2500); // Cooldown time from previous batch
    }


    private static RuntimeException complexException() {
        StackTraceElement mainMethod = new StackTraceElement("org.logeventsdemo.MyApplication", "main", "MyApplication.java", 20);
        StackTraceElement publicMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "publicMethod", "MyClassName.java", 31);
        StackTraceElement internalMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "internalMethod", "MyClassName.java", 311);
        StackTraceElement nioInternalMethod = new StackTraceElement("sun.nio.fs.WindowsException", "translateToIOException", "WindowsException.java", 79);
        StackTraceElement ioApiMethod = new StackTraceElement("java.io.FilterOutputStream", "close", "FilterOutputStream.java", 180);
        StackTraceElement ioInternalMethod = new StackTraceElement("java.io.FileOutputStream", "close", "FileOutputStream.java", 323);
        IOException nestedNested = new IOException("Nested nested");
        nestedNested.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod, nioInternalMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        IOException nested = new IOException("Nested", nestedNested);
        nested.setStackTrace(new StackTraceElement[] {
                ioApiMethod, nioInternalMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        RuntimeException exception = new RuntimeException("This is an error message", nested);
        exception.setStackTrace(new StackTraceElement[] {
                internalMethod, publicMethod, mainMethod
        });
        return exception;
    }


}
