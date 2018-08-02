package org.logeventsdemo.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.logevents.LogEventFactory;
import org.logevents.extend.servlets.LogEventsConfigurationServlet;
import org.logevents.extend.servlets.LogEventsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class Main {

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static class RootServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            logger.info("Request from {} to {}", req.getRemoteAddr(), req.getPathInfo());
            resp.getWriter().print("Hello World");
        }

    }

    public static void main(String[] args) throws LifecycleException {
        LogEventFactory factory = LogEventFactory.getInstance();
        factory.setLevel(factory.getLogger("org.logeventsdemo"), Level.DEBUG);
        factory.setLevel(factory.getRootLogger(), Level.INFO);

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(4000);

        Context context = tomcat.addContext("", null);

        Tomcat.addServlet(context, "rootServlet", new RootServlet());
        context.addServletMappingDecoded("/*", "rootServlet");

        Wrapper logServlet = Tomcat.addServlet(context, "logsServlet", new LogEventsServlet());
        logServlet.setLoadOnStartup(1);
        logServlet.addMapping("/logs");

        Tomcat.addServlet(context, "logsConfigServlet", new LogEventsConfigurationServlet());
        context.addServletMappingDecoded("/logs/config", "logsConfigServlet");

        tomcat.start();
        tomcat.getServer().await();
    }

}
