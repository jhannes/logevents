package org.logevents.extend.servlets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;

public class LogEventsServlet extends HttpServlet {

    private LogEventFormatter formatter = new TTLLEventLogFormatter();

    private CircularBufferLogEventObserver debugObserver = new CircularBufferLogEventObserver();
    private CircularBufferLogEventObserver infoObserver = new CircularBufferLogEventObserver();
    private CircularBufferLogEventObserver warnObserver = new CircularBufferLogEventObserver();
    private CircularBufferLogEventObserver errorObserver = new CircularBufferLogEventObserver();

    @Override
    public void init(ServletConfig config) throws ServletException {
        attachLogEventObservers(LogEventFactory.getInstance());
    }

    void attachLogEventObservers(LogEventFactory factory) {
        factory.addObserver(factory.getRootLogger(), levelObserver(debugObserver, Level.DEBUG));
        factory.addObserver(factory.getRootLogger(), levelObserver(infoObserver, Level.INFO));
        factory.addObserver(factory.getRootLogger(), levelObserver(warnObserver, Level.WARN));
        factory.addObserver(factory.getRootLogger(), levelObserver(errorObserver, Level.ERROR));
    }

    private LogEventObserver levelObserver(LogEventObserver delegate, Level level) {
        return new LogEventObserver() {
            @Override
            public void logEvent(LogEvent e) {
                if (e.getLevel().equals(level)) {
                    delegate.logEvent(e);
                }
            }
            @Override
            public String toString() {
                return "LevelConditionalObserver{level=" + level + ",delegate=" + delegate + "}";
            }
        };
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> result = getLogEvents();
        resp.setContentType("application/json");
        resp.getWriter().write(JsonUtil.toIndentedJson(result));
    }

    Map<String, Object> getLogEvents() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("debug", convert(debugObserver.getEvents()));
        result.put("info", convert(infoObserver.getEvents()));
        result.put("warn", convert(warnObserver.getEvents()));
        result.put("error", convert(errorObserver.getEvents()));
        return result;
    }

    private List<String> convert(Collection<LogEvent> events) {
        ArrayList<String> result = new ArrayList<>();
        for (LogEvent event : events) {
            result.add(formatter.apply(event));
        }
        return result;
    }

}
