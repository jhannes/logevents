package org.logeventsdemo.servlets;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;

import static org.junit.jupiter.api.Assertions.*;

class LogViewerServerTest {

    @Test
    void shouldShowLogViewServer() throws Exception {
        LogViewerServer server = new LogViewerServer();
        server.start(0);
        HttpURLConnection connection = (HttpURLConnection) server.getURI().toURL().openConnection();
        assertEquals(200, connection.getResponseCode());
    }
}
