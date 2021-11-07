package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.logevents.config.Configuration;
import org.logevents.observers.DatabaseLogEventObserver;
import org.logevents.util.openid.OpenIdConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class LogViewerServer {

    private static final Logger logger = LoggerFactory.getLogger(LogViewerServer.class);

    private final Server server = new Server();
    private final ServerConnector connector = new ServerConnector(server);
    private final LogViewerApplication listener = new LogViewerApplication();

    public LogViewerServer() {
        server.setHandler(createWebAppContext(listener));
        server.addConnector(connector);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> properties = loadProperties("logviewer.properties");

        LogViewerServer server = new LogViewerServer();
        server.setConfiguration(properties);
        server.start(8080);

        logger.info("Started server {}", server.getURI());
    }

    private static Map<String, String> loadProperties(String filename) throws IOException {
        Map<String, String> properties = new HashMap<>();
        try (FileReader reader = new FileReader(filename)) {
            Properties props = new Properties();
            props.load(reader);
            props.forEach((k, v) -> properties.put(k.toString(), v.toString()));

        }
        return properties;
    }

    public void setConfiguration(Map<String, String> properties) {
        listener.setOpenIdConfiguration(new OpenIdConfiguration(new Configuration(properties, "openid")));
        listener.setLogEventSource(new DatabaseLogEventObserver(properties, "database"));
        listener.setCookieEncryptionKey(properties.get("cookie.secret"));
    }

    public void start(int port) throws Exception {
        server.start();
        setPort(port);
    }

    private void setPort(int port) throws Exception {
        connector.stop();
        connector.setPort(port);
        connector.start();
    }

    public static ServletContextHandler createWebAppContext(LogViewerApplication listener) {
        ServletContextHandler webApp = new ServletContextHandler();
        webApp.addEventListener(listener);
        return webApp;
    }

    public URI getURI() {
        return server.getURI();
    }
}
