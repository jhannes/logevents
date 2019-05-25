package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Server;
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
import java.util.TimeZone;

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
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LogEventFactory factory = LogEventFactory.getInstance();
        factory.setLevel(factory.getLogger("org.logeventsdemo"), Level.DEBUG);
        factory.setLevel(factory.getRootLogger(), Level.INFO);

        new Thread(Main::makeNoise).start();

        Server server = new Server(Integer.parseInt(System.getProperty("httpPort", "4000")));
        server.setHandler(demoContext());

        server.start();
        logger.warn(LIFECYCLE, "Started server {}", server.getURI());
    }

    private static WebAppContext demoContext() {
        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        context.setBaseResource(Resource.newClassPathResource("/webapp-logevents"));
        //context.setBaseResource(Resource.newResource("src/main/resources/webapp-logevents"));
        context.getInitParams().put("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        context.addEventListener(new ApplicationContext());
        return context;
    }

    private static final Random random = new Random();

    @SafeVarargs
    private static <T> T pickOne(T... options) {
        return options[random.nextInt(options.length)];
    }

    private static void makeNoise() {
        try {
            while (true) {
                Thread.sleep(random.nextInt(10000));

                try {
                    if (random.nextInt(100) < 80) {
                        MDC.put("clientIp", pickOne("127.0.0.1", "10.10.0." + random.nextInt(255)));
                    }
                    if (random.nextInt(100) < 50) {
                        MDC.put("request", pickOne("/api/operation", "/api/resources", "/orders", "/customer/orders"));
                    }
                    if (random.nextInt(100) < 30) {
                        MDC.put("user", pickOne("alice", "bob", "charlie", "diana") + "@example." + pickOne("com", "org", "net"));
                    }
                    logger.log(
                            pickOne(OPS, SECRET, HTTP, null),
                            null,
                            pickOne(Level.ERROR, Level.WARN, Level.INFO).toInt(),
                            pickOne("Test message {}", "Other {} test message", "Even more", "Message with <a href='#'>Embedded HTML</a>"),
                            new Object[] { random.nextBoolean() ? random.nextInt(100) : "<a href='#'>A link</a>"},
                            random.nextInt(100) < 20 ? new IOException("Some error") : null);
                } finally {
                    MDC.clear();
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
