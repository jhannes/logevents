package org.logevents.extend.servlets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.logevents.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class LogEventsConfigurationServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(LogEventsConfigurationServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LogEventFactory logEventFactory = logEventFactory();
        Map<String, LoggerConfiguration> loggers = logEventFactory.getLoggers();

        List<String> loggerNames = new ArrayList<>(loggers.keySet());
        Collections.sort(loggerNames);

        Map<String, String> logLevels = new LinkedHashMap<>();
        logLevels.put("/", logEventFactory.getRootLogger().getLevelThreshold().toString());
        for (String loggerName : loggerNames) {
            Level threshold = logEventFactory.getLogger(loggerName).getLevelThreshold();
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

        resp.setContentType("application/json");
        resp.getWriter().println(JsonUtil.toJson(configuration));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String loggerName = req.getParameter("loggerName");
        String levelName = req.getParameter("level");
        Level level = levelName.equals("null") ? null : Level.valueOf(levelName);
        logger.info("Changing log level for {} to {}", loggerName, level);

        LoggerConfiguration loggerConfiguration = logEventFactory().getLogger(loggerName);
        logEventFactory().setLevel(loggerConfiguration,
                level);

        resp.sendRedirect(req.getContextPath() + req.getServletPath() + req.getPathInfo());
    }

    private LogEventFactory logEventFactory() {
        return LogEventFactory.getInstance();
    }

}
