package org.logevents.optional.jakarta;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.util.JsonUtil;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.logevents.util.JsonUtil.getField;
import static org.logevents.util.JsonUtil.getObject;

public class LogEventsConfigurationServletTest {

    private LogEventsConfigurationServlet servlet = new LogEventsConfigurationServlet();
    private LogEventFactory factory = LogEventFactory.getInstance();

    @Test
    public void shouldTranslateLogLevelsToJson() {
        servlet.setLogLevel("org.example", "TRACE");

        Map<String, Object> json = servlet.logConfigurationToJson(factory);
        Map<String, Object> levels = JsonUtil.getObject(json, "logLevels");

        assertEquals(factory.getRootLogger().getOwnFilter().toString(),
                JsonUtil.getField(levels, "/"));
        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG,TRACE}", JsonUtil.getField(levels, "org.example"));
        assertEquals("<inherited>", JsonUtil.getField(levels, "org"));
    }

    @Test
    public void shouldResetLogLevel() {
        servlet.setLogLevel("org.example", "TRACE");

        assertEquals("LogEventFilter{ERROR,WARN,INFO,DEBUG,TRACE}",
                getField(getObject(servlet.logConfigurationToJson(factory), "logLevels"), "org.example"));

        servlet.setLogLevel("org.example", "null");
        assertEquals("<inherited>",
                getField(getObject(servlet.logConfigurationToJson(factory), "logLevels"), "org.example"));

    }

    @Test
    public void shouldTranslateLogObserversToJson() {
        factory.setRootObserver(new CircularBufferLogEventObserver());
        factory.setObserver("org.example", new CircularBufferLogEventObserver());
        Map<String, Object> json = servlet.logConfigurationToJson(factory);

        Map<String, Object> observers = JsonUtil.getObject(json, "observers");
        assertEquals("CircularBufferLogEventObserver{size=0,capacity=200}",
                JsonUtil.getField(observers, "/"));
        assertEquals("CompositeLogEventObserver{[CircularBufferLogEventObserver{size=0,capacity=200}, CircularBufferLogEventObserver{size=0,capacity=200}]}",
                JsonUtil.getField(observers, "org.example"));
        assertEquals("CircularBufferLogEventObserver{size=0,capacity=200}",
                JsonUtil.getField(observers, "org"));
    }

}
