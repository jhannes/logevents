package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.logevents.optional.servlets.LogEventsServlet;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LocationAwareLogger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Random;

public class DemoServer {

    private static final Marker OPS = MarkerFactory.getMarker("OPS");
    private static final Marker LIFECYCLE = MarkerFactory.getMarker("LIFECYCLE");
    private static final Marker SECRET = MarkerFactory.getMarker("SECRET");
    private static final Marker HTTP = MarkerFactory.getMarker("HTTP");
    static {
        OPS.add(LIFECYCLE); OPS.add(HTTP);
    }

    private static LocationAwareLogger logger = (LocationAwareLogger) LoggerFactory.getLogger(DemoServer.class);
    private Server server;

    public DemoServer(int httpPort) {
        server = new Server(httpPort);
        server.setHandler(demoContext());
    }

    public static void main(String[] args) throws Exception {
        DemoServer server = new DemoServer(Integer.parseInt(System.getProperty("httpPort", "4000")));
        server.start();
        logger.warn(LIFECYCLE, "Started server {}", server.getURI());
    }

    public URI getURI() {
        return server.getURI();
    }

    void start() throws Exception {
        new Thread(DemoServer::makeNoise).start();
        server.start();
    }

    private static WebAppContext demoContext() {
        WebAppContext context = new WebAppContext();
        context.setContextPath("/");

        URL webAppResource = DemoServer.class.getResource("/webapp-logevents");
        File webAppSource = new File(webAppResource.getPath().replaceAll("/target/classes/", "/src/main/resources/"));
        if (webAppSource.isDirectory()) {
            context.setBaseResource(Resource.newResource(webAppSource));
            context.getInitParams().put("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        } else {
            context.setBaseResource(Resource.newResource(webAppResource));
        }

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
                Thread.sleep(random.nextInt(10000));
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
