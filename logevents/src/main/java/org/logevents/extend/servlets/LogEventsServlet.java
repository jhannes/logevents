package org.logevents.extend.servlets;

import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.logevents.util.JsonUtil;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LogEventsServlet extends HttpServlet {

    private LogEventFormatter formatter = new TTLLEventLogFormatter();

    private CircularBufferLogEventObserver debugObserver = new CircularBufferLogEventObserver();
    private CircularBufferLogEventObserver infoObserver = new CircularBufferLogEventObserver();
    private CircularBufferLogEventObserver warnObserver = new CircularBufferLogEventObserver();
    private CircularBufferLogEventObserver errorObserver = new CircularBufferLogEventObserver();
    private String logeventsHtml = "/org/logevents/logevents.html";

    @Override
    public void init(ServletConfig config) {
        attachLogEventObservers(LogEventFactory.getInstance());
    }

    void attachLogEventObservers(LogEventFactory factory) {
        factory.addRootObserver(levelObserver(debugObserver, Level.DEBUG));
        factory.addRootObserver(levelObserver(infoObserver, Level.INFO));
        factory.addRootObserver(levelObserver(warnObserver, Level.WARN));
        factory.addRootObserver(levelObserver(errorObserver, Level.ERROR));
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        if (req.getPathInfo() == null) {
            try (InputStream html = getClass().getResourceAsStream(logeventsHtml)) {
                int c;
                while ((c = html.read()) != -1) {
                    resp.getOutputStream().write((byte) c);
                }
            }
            resp.setContentType("text/html");
        } else if (req.getPathInfo().equals("/events") || req.getPathInfo().equals("/logs/events")) {
            Map<String, Object> result = new HashMap<>();


            Level level = Optional.ofNullable(req.getParameter("level")).map(Level::valueOf).orElse(Level.INFO);

            List<LogEvent> events = new ArrayList<>();
            events.addAll(infoObserver.getEvents());
            events.addAll(warnObserver.getEvents());
            events.addAll(errorObserver.getEvents());
            events.sort(Comparator.comparing(LogEvent::getInstant));

            Set<String> markers = new HashSet<>();

            for (LogEvent event : events) {
                if (event.getMarker() != null) {
                    markers.add(event.getMarker().getName());
                }
            }

            HashMap<Object, Object> facets = new HashMap<>();
            facets.put("markers", markers);

            List<Map<String, Object>> jsonEvents = new ArrayList<>();
            for (LogEvent event : events) {
                Map<String, Object> jsonEvent = new HashMap<>();

                jsonEvent.put("thread", event.getThreadName());
                jsonEvent.put("time", event.getInstant().toString());
                jsonEvent.put("logger", event.getLoggerName());
                jsonEvent.put("abbreviatedLogger", event.getAbbreviatedLoggerName(0));
                jsonEvent.put("level", event.getLevel().name());
                jsonEvent.put("levelIcon", JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()));
                jsonEvent.put("formattedMessage", event.formatMessage());
                jsonEvent.put("messageTemplate", event.getMessage());
                jsonEvent.put("marker", Optional.ofNullable(event.getMarker()).map(Marker::getName).orElse(null));

                if (event.getThrowable() != null) {
                    jsonEvent.put("throwable", event.getThrowable().toString());
                    ArrayList<Map<String, String>> stackTrace = new ArrayList<>();
                    for (StackTraceElement element : event.getThrowable().getStackTrace()) {
                        Map<String, String> jsonElement = new HashMap<>();
                        jsonElement.put("className", element.getClassName());
                        jsonElement.put("methodName", element.getMethodName());
                        jsonElement.put("lineNumber", String.valueOf(element.getLineNumber()));
                        jsonElement.put("fileName", element.getFileName());
                        stackTrace.add(jsonElement);
                    }
                    jsonEvent.put("stackTrace", stackTrace);
                }

                jsonEvent.put("mdc", event.getMdcProperties());

                jsonEvents.add(jsonEvent);
            }


            result.put("facets", facets);
            result.put("eventsText", convert(events));
            result.put("events", jsonEvents);


            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        } else {
            Map<String, Object> result = getLogEvents();
            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        }
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
