package org.logevents;

import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.core.AbstractFilteredLogEventObserver;
import org.logevents.core.CompositeLogEventObserver;
import org.logevents.core.LoggerDelegator;
import org.logevents.formatters.ConsoleLogEventFormatter;
import org.logevents.optional.junit.LogEventSampler;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class LogEventTest {

    @Test
    public void shouldFindLoggingClassWithSlf4j() {
        LogEvent event = new LogEventSampler().build();
        StackTraceElement callerLocation = event.extractCallerLocation(new StackTraceElement[]{
                new StackTraceElement(LogEvent.class.getName(), "extractCallerLocation", "LogEvent.java", 100),
                new StackTraceElement(LogEvent.class.getName(), "getCallerLocation", "LogEvent.java", 90),
                new StackTraceElement(ConsoleLogEventFormatter.class.getName(), "apply", "ConsoleLogEventFormatter.java", 90),
                new StackTraceElement(AbstractFilteredLogEventObserver.class.getName(), "logEvent", "AbstractFilteredLogEventObserver.java", 90),
                new StackTraceElement(CompositeLogEventObserver.class.getName(), "logEvent", "CompositeLogEventObserver.java", 90),
                new StackTraceElement("org.logevents.core.LevelLoggingEventGenerator", "log", "LevelLoggingEventGenerator.java", 87),
                new StackTraceElement("org.logevents.core.LevelLoggingEventGenerator", "log", "LevelLoggingEventGenerator.java", 33),
                new StackTraceElement(LoggerDelegator.class.getName(), "warn", "LoggerDelegator.java", 62),
                new StackTraceElement("org.example.MyTest", "main", null, -1),
        });
        assertEquals("org.example.MyTest", callerLocation.getClassName());
        assertEquals("main", callerLocation.getMethodName());
    }


    @Test
    public void shouldFindLoggingClassWithJavaUtilLogging() {
        LogEvent event = new LogEventSampler().build();
        StackTraceElement callerLocation = event.extractCallerLocation(new StackTraceElement[]{
                new StackTraceElement(LogEvent.class.getName(), "extractCallerLocation", "LogEvent.java", 100),
                new StackTraceElement(LogEvent.class.getName(), "getCallerLocation", "LogEvent.java", 90),
                new StackTraceElement(ConsoleLogEventFormatter.class.getName(), "apply", "ConsoleLogEventFormatter.java", 90),
                new StackTraceElement(AbstractFilteredLogEventObserver.class.getName(), "logEvent", "AbstractFilteredLogEventObserver.java", 90),
                new StackTraceElement(CompositeLogEventObserver.class.getName(), "logEvent", "CompositeLogEventObserver.java", 90),
                new StackTraceElement("org.logevents.core.LevelLoggingEventGenerator", "log", "LevelLoggingEventGenerator.java", 87),
                new StackTraceElement("org.logevents.core.LevelLoggingEventGenerator", "log", "LevelLoggingEventGenerator.java", 33),
                new StackTraceElement("org.logevents.core.JavaUtilLoggingAdapter", "publish", "JavaUtilLoggingAdapter.java", 62),
                new StackTraceElement("java.util.logging.Logger", "log", null, -1),
                new StackTraceElement("java.util.logging.Logger", "doLog", null, -1),
                new StackTraceElement("java.util.logging.Logger", "log", null, -1),
                new StackTraceElement("sun.util.logging.internal.LoggingProviderImpl$JULWrapper", "log", null, -1),
                new StackTraceElement("sun.util.logging.PlatformLogger", "fine", null, -1),
                new StackTraceElement("sun.net.www.protocol.http.HttpURLConnection", "getInputStream0", null, -1),
                new StackTraceElement("sun.net.www.protocol.http.HttpURLConnection", "getInputStream", null, -1),
                new StackTraceElement("java.net.HttpURLConnection", "getResponseCode", null, -1),
        });
        assertEquals("sun.net.www.protocol.http.HttpURLConnection", callerLocation.getClassName());
        assertEquals("getInputStream0", callerLocation.getMethodName());
    }


    private final Instant time = ZonedDateTime.of(2018, 8, 1, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant();

    @Test
    public void shouldLogMainClassWithClassnameInsteadOfLogger() {
        ConsoleLogEventFormatter formatter = new ConsoleLogEventFormatter();
        formatter.configure(new Configuration(new Properties(), ""));
        LogEvent logEvent = new LogEventSampler()
                .withLevel(Level.INFO)
                .withTime(time)
                .withThread("main")
                .withFormat("Hello there")
                .build();
        String mainClassName = Configuration.getMainClassName().orElseThrow(AssertionError::new);
        int lastDotPos = mainClassName.lastIndexOf('.');
        String simpleMainClass = mainClassName.substring(lastDotPos+1);

        logEvent.setCallerLocation(new StackTraceElement(mainClassName, "theCallingMethod", "MyFile.java", 213));
        String formatted = formatter.apply(logEvent);
        assertEquals("10:00:00.000 [main] [\033[34mINFO \033[m] [\033[1;m" + simpleMainClass + ".theCallingMethod(MyFile.java:213)\033[m]: Hello there\n",
                formatted);

    }
}
