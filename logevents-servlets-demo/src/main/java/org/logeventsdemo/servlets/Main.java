package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.logevents.LogEventFactory;
import org.logevents.extend.servlets.LogEventsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class Main {

    private static final Marker OPS = MarkerFactory.getMarker("OPS");
    private static final Marker LIFECYCLE = MarkerFactory.getMarker("LIFECYCLE");
    private static final Marker HTTP = MarkerFactory.getMarker("HTTP");
    static {
        OPS.add(LIFECYCLE); OPS.add(HTTP);
    }

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static class RootServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            logger.info(HTTP, "Request from {} to {}", req.getRemoteAddr(), req.getPathInfo());
            resp.getWriter().print("Hello World");
        }

    }

    public static void main(String[] args) throws Exception {
        LogEventFactory factory = LogEventFactory.getInstance();
        factory.setLevel(factory.getLogger("org.logeventsdemo"), Level.DEBUG);
        factory.setLevel(factory.getRootLogger(), Level.INFO);

        Server server = new Server(4000);

        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        context.setBaseResource(Resource.newClassPathResource("/webapp-logevents"));
        context.addEventListener(new ApplicationContext());

        server.setHandler(context);

        server.start();
        logger.warn(LIFECYCLE, "Started server {}", server.getURI());
    }

    private static class ApplicationContext implements ServletContextListener {
        @Override
        public void contextInitialized(ServletContextEvent sce) {
            sce.getServletContext().addServlet("root", new RootServlet()).addMapping("/*");
            sce.getServletContext().addServlet("logs", new LogEventsServlet()).addMapping("/logs/*");
            sce.getServletContext().addServlet("logsConfig", new RootServlet()).addMapping("/logs/config");
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {

        }
    }
}
