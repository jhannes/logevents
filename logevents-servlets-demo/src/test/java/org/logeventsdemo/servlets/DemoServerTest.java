package org.logeventsdemo.servlets;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoServerTest {

    @Test
    void shouldViewLogEvents() throws Exception {
        DemoServer server = new DemoServer(0);
        server.start();

        HttpURLConnection connection = (HttpURLConnection) server.getURI().toURL().openConnection();
        assertEquals(200, connection.getResponseCode());
    }

    @Test
    void shouldViewSwaggerDashboard() throws Exception {
        DemoServer server = new DemoServer(0);
        server.start();

        URL url = new URL(server.getURI() + "./swagger/swagger-ui-bundle.js");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertEquals(200, connection.getResponseCode());
        assertEquals("text/javascript", connection.getHeaderField("Content-type"));
    }

    @Test
    public void shouldWarnOnMissingSwaggerResource() throws Exception {
        DemoServer server = new DemoServer(0);
        server.start();

        URL url = new URL(server.getURI() + "./swagger/nothing-here-but-us-chickens.js");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertEquals(404, connection.getResponseCode());
    }

}
