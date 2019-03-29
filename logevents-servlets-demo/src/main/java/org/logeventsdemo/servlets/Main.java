package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.logevents.LogEventFactory;
import org.logevents.extend.servlets.LogEventsServlet;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LocationAwareLogger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.IOException;
import java.util.Random;

public class Main {

    private static final Marker OPS = MarkerFactory.getMarker("OPS");
    private static final Marker LIFECYCLE = MarkerFactory.getMarker("LIFECYCLE");
    private static final Marker SECRET = MarkerFactory.getMarker("SECRET");
    private static final Marker HTTP = MarkerFactory.getMarker("HTTP");
    static {
        OPS.add(LIFECYCLE); OPS.add(HTTP);
    }

    private static LocationAwareLogger logger = (LocationAwareLogger) LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        LogEventFactory factory = LogEventFactory.getInstance();
        factory.setLevel(factory.getLogger("org.logeventsdemo"), Level.DEBUG);
        factory.setLevel(factory.getRootLogger(), Level.INFO);

        new Thread(Main::makeNoise).start();

        Server server = new Server(4000);

        HandlerList handlers = new HandlerList();

        handlers.addHandler(demoContext());

        server.setHandler(handlers);

        server.start();
        logger.warn(LIFECYCLE, "Started server {}", server.getURI());
    }

    private static WebAppContext demoContext() {
        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        context.setBaseResource(Resource.newClassPathResource("/webapp-logevents"));
        context.addEventListener(new ApplicationContext());
        return context;
    }

    private static final Random random = new Random();

    private static <T> T pickOne(T... options) {
        return options[random.nextInt(options.length)];
    }

    private static void makeNoise() {
        try {
            while (true) {
                Thread.sleep(random.nextInt(10000));

                try (MDC.MDCCloseable mdcCloseable = MDC.putCloseable(
                        pickOne("one", "two", "three"),
                        pickOne("a", "b", "c", "d", "e", "f", "g", "h"))
                ) {
                    logger.log(
                            pickOne(OPS, SECRET, HTTP, null),
                            null,
                            pickOne(Level.ERROR, Level.WARN, Level.INFO).toInt(),
                            pickOne("Test message {}", "Other {} test message", "Even more"),
                            new Object[] { random.nextInt(100)},
                            random.nextInt(100) < 20 ? new IOException("Some error") : null);
                }
            }
        } catch (InterruptedException ignore) {

        }
    }

    private static class ApplicationContext implements ServletContextListener {
        @Override
        public void contextInitialized(ServletContextEvent sce) {
            sce.getServletContext().addServlet("logs", new LogEventsServlet()).addMapping("/logs/*");

            sce.getServletContext().addServlet("swagger", new WebJarServlet("swagger-ui"))
                    .addMapping("/swagger/*");
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {

        }
    }
}
