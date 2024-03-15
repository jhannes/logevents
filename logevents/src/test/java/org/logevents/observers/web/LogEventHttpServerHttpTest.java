package org.logevents.observers.web;

import org.junit.Before;
import org.junit.Test;
import org.logevents.util.NetUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogEventHttpServerHttpTest {

    private LogEventHttpServer server;

    @Before
    public void setUp() throws Exception {
        server = new LogEventHttpServer();
    }

    @Test
    public void shouldFetchLoggingPageOnHttp() throws IOException {
        LogEventHttpServer server = new LogEventHttpServer();
        server.setHttpPort(java.util.Optional.of(0));
        server.start();

        String frontPage = readURL(server.getUrl());
        assertTrue(frontPage + " should contain " + "<title>LogEvents Dashboard</title>",
                frontPage.contains("<title>LogEvents Dashboard</title>"));

        String style = readURL(server.getUrl() + "/logevents.css");
        assertTrue(style + " should contain 'nav .closeDrawer {'",
                style.contains("nav .closeDrawer {"));

        String script = readURL(server.getUrl() + "/logevents-common.js");
        assertTrue(script + " should contain 'function createElementWithText'",
                script.contains("function createElementWithText"));
    }

    @Test
    public void shouldReturn404OnUnknownFile() throws IOException {
        LogEventHttpServer server = new LogEventHttpServer();
        server.setHttpPort(java.util.Optional.of(0));
        server.start();
        HttpURLConnection connection = (HttpURLConnection) new URL(server.getUrl() + "/logevents-missing.js").openConnection();
        assertEquals(404, connection.getResponseCode());
    }

    private String readURL(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        return NetUtils.readAsString(connection.getInputStream());
    }
}
