package org.logevents.optional.servlets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.logevents.LogEventFactory;
import org.logevents.LogEventLogger;
import org.logevents.core.LogEventFilter;
import org.logevents.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class LogEventsConfigurationServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(LogEventsConfigurationServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> configuration = logConfigurationToJson(LogEventFactory.getInstance());
        resp.setContentType("application/json");
        resp.getWriter().println(JsonUtil.toIndentedJson(configuration));
    }

    Map<String, Object> logConfigurationToJson(LogEventFactory logEventFactory) {
        Map<String, LogEventLogger> loggers = logEventFactory.getLoggers();

        List<String> loggerNames = new ArrayList<>(loggers.keySet());
        Collections.sort(loggerNames);

        Map<String, String> logLevels = new LinkedHashMap<>();
        // TODO: null check?
        logLevels.put("/", logEventFactory.getRootLogger().getOwnFilter().toString());
        for (String loggerName : loggerNames) {
            LogEventFilter threshold = logEventFactory.getLogger(loggerName).getOwnFilter();
            logLevels.put(loggerName, threshold != null ? threshold.toString() : "<inherited>");
        }

        Map<String, String> observers = new LinkedHashMap<>();
        observers.put("/", logEventFactory.getRootLogger().getObserver());
        for (String loggerName : loggerNames) {
            observers.put(loggerName, logEventFactory.getLogger(loggerName).getObserver());
        }

        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("logLevels", logLevels);
        configuration.put("observers", observers);
        return configuration;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setLogLevel(req.getParameter("loggerName"), req.getParameter("level"));
        resp.sendRedirect(req.getContextPath() + req.getServletPath() + req.getPathInfo());
    }

    void setLogLevel(String loggerName, String levelName) {
        Level level = levelName == null || levelName.equals("null") ? null : Level.valueOf(levelName);
        logger.info("Changing log level for {} to {}", loggerName, level);

        LogEventFactory.getInstance().setLevel(loggerName, level);
    }

}
