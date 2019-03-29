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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LogEventsServlet extends HttpServlet {

    private LogEventFormatter formatter = new TTLLEventLogFormatter();

    private Map<Level, CircularBufferLogEventObserver> messages = new HashMap<>();
    private String logeventsHtml = "/org/logevents/logevents.html";
    private String logeventsApi = "/org/logevents/swagger.json";

    @Override
    public void init(ServletConfig config) {
        attachLogEventObservers(LogEventFactory.getInstance());
    }

    void attachLogEventObservers(LogEventFactory factory) {
        for (Level level : Level.values()) {
            CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
            this.messages.put(level, observer);
            factory.addRootObserver(levelObserver(observer, level));
        }
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
            resp.setContentType("text/html");
            copyResource(resp, logeventsHtml);
        } else if (req.getPathInfo().equals("/swagger.json")) {
            resp.setContentType("application/json");
            copyResource(resp, logeventsApi);
        } else if (req.getPathInfo().equals("/events") || req.getPathInfo().equals("/logs/events")) {
            Map<String, Object> result = new LinkedHashMap<>();

            LogEventFilter filter = new LogEventFilter(req.getParameterMap());
            List<LogEvent> events = filter.collect(messages);

            Set<String> markers = new HashSet<>();
            Set<String> threads = new HashSet<>();
            Map<String, Set<String>> mdcMap = new HashMap<>();

            for (LogEvent event : events) {
                if (event.getMarker() != null) {
                    markers.add(event.getMarker().getName());
                }
                threads.add(event.getThreadName());
                for (String mdcKey : event.getMdcProperties().keySet()) {
                    mdcMap.computeIfAbsent(mdcKey, k -> new HashSet<>()).add(event.getMdcProperties().get(mdcKey));
                }
            }
            List<Map<String, Object>> mdc = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : mdcMap.entrySet()) {
                Map<String, Object> mdcEntry = new HashMap<>();
                mdcEntry.put("name", entry.getKey());
                mdcEntry.put("values", entry.getValue());
            }


            HashMap<Object, Object> facets = new HashMap<>();
            facets.put("markers", markers);
            facets.put("threads", threads);
            facets.put("mdc", mdc);

            List<Map<String, Object>> jsonEvents = new ArrayList<>();
            for (LogEvent event : events) {
                jsonEvents.add(formatAsJson(event));
            }

            result.put("facets", facets);
            result.put("events", jsonEvents);

            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        } else {
            Map<String, Object> result = getLogEvents();
            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        }
    }

    private void copyResource(HttpServletResponse resp, String resource) throws IOException {
        try (InputStream html = getClass().getResourceAsStream(resource)) {
            int c;
            while ((c = html.read()) != -1) {
                resp.getOutputStream().write((byte) c);
            }
        }
    }

    private Map<String, Object> formatAsJson(LogEvent event) {
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
            ArrayList<Map<String, Object>> stackTrace = new ArrayList<>();
            for (StackTraceElement element : event.getThrowable().getStackTrace()) {
                Map<String, Object> jsonElement = new HashMap<>();
                jsonElement.put("className", element.getClassName());
                jsonElement.put("methodName", element.getMethodName());
                jsonElement.put("lineNumber", String.valueOf(element.getLineNumber()));
                jsonElement.put("fileName", element.getFileName());
                stackTrace.add(jsonElement);
            }
            jsonEvent.put("stackTrace", stackTrace);
        }

        List<Object> mdc = new ArrayList<>();
        for (Map.Entry<String, String> entry : event.getMdcProperties().entrySet()) {
            Map<String, String> mdcEntry = new HashMap<>();
            mdcEntry.put("name", entry.getKey());
            mdcEntry.put("value", entry.getValue());
            mdc.add(mdcEntry);
        }
        jsonEvent.put("mdc", mdc);
        return jsonEvent;
    }

    Map<String, Object> getLogEvents() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<Level, CircularBufferLogEventObserver> entry : messages.entrySet()) {
            result.put(entry.getKey().name().toLowerCase(), convert(entry.getValue().getEvents()));
        }
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
