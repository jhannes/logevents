package org.logevents;

import org.junit.Test;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.formatters.ConsoleLogEventFormatter;
import org.logevents.core.LoggerDelegator;
import org.logevents.core.AbstractFilteredLogEventObserver;
import org.logevents.core.CompositeLogEventObserver;

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
}
