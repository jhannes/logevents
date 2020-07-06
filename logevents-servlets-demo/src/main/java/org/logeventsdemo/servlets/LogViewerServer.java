package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.logevents.config.Configuration;
import org.logevents.observers.DatabaseLogEventObserver;
import org.logevents.util.openid.OpenIdConfiguration;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class LogViewerServer {

    private final Server server = new Server();
    private final ServerConnector connector = new ServerConnector(server);
    private final LogViewerApplication listener = new LogViewerApplication();

    public LogViewerServer() {
        server.setHandler(createWebAppContext(listener));
    }

    public static void main(String[] args) throws Exception {
        Properties properties = loadProperties("logviewer.properties");
        LogViewerServer server = new LogViewerServer();
        server.setConfiguration(properties);
        server.start();
    }

    private static Properties loadProperties(String filename) throws IOException {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(filename)) {
            properties.load(reader);
        }
        return properties;
    }

    private void setConfiguration(Properties properties) {
        listener.setOpenIdConfiguration(new OpenIdConfiguration(new Configuration(properties, "openid")));
        listener.setLogEventSource(new DatabaseLogEventObserver(properties, "database"));
        listener.setCookieEncryptionKey(properties.getProperty("cookieEncryptionKey"));
    }

    private void start() throws Exception {
        server.start();
        setPort(8080);
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


}
