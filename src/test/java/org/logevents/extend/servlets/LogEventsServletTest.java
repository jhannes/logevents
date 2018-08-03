package org.logevents.extend.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.observers.NullLogEventObserver;
import org.logevents.util.JsonUtil;
import org.slf4j.Logger;

public class LogEventsServletTest extends LogEventsServlet {

    private LogEventsServlet servlet = new LogEventsServlet();
    private Logger logger = LogEventFactory.getInstance().getLogger(getClass().getName());

    @Test
    public void shouldShowLogEvents() {
        LogEventFactory.getInstance().setRootObserver(new NullLogEventObserver());
        servlet.attachLogEventObservers(LogEventFactory.getInstance());

        String errorMessage = "An error message";
        logger.error(errorMessage);

        Map<String, Object> logEvents = servlet.getLogEvents();
        assertEquals(Collections.emptyList(), JsonUtil.getList(logEvents, "info"));

        List<Object> errorMessages = JsonUtil.getList(logEvents, "error");
        assertTrue("errorMessage should contain one message, was " + errorMessages,
                errorMessages.size() == 1);
        assertTrue("Expected to find " + errorMessage + " in " + errorMessages.get(0),
                errorMessages.get(0).toString().contains(errorMessage));
    }

    @Test
    public void shouldOmitLogEventsBelowThreshold() {
        servlet.attachLogEventObservers(LogEventFactory.getInstance());

        logger.debug("A debug message");

        Map<String, Object> logEvents = servlet.getLogEvents();
        assertEquals(Collections.emptyList(), JsonUtil.getList(logEvents, "debug"));
    }

}
