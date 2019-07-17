package org.logevents.util.openid;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.logevents.util.JsonUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OpenIdConfigurationTest {

    @Test
    public void shouldFetchIdToken() throws IOException {
        String subject = "sdgnsldgnlk32";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/.well-known/openid-configuration", t -> {
            Map<String, String> discoveryDoc = new HashMap<>();
            discoveryDoc.put("token_endpoint", "http://localhost:" + t.getLocalAddress().getPort() + "/token");
            writeResponse(t, JsonUtil.toIndentedJson(discoveryDoc).getBytes());
        });
        server.createContext("/token", t -> {
            Map<String, String> idTokenHeader = new HashMap<>();
            Map<String, String> idTokenPayload = new HashMap<>();
            idTokenPayload.put("sub", subject);
            String idToken = JsonUtil.base64Encode(idTokenHeader) + "." + JsonUtil.base64Encode(idTokenPayload) + ".";

            Map<String, String> tokenResponse = new HashMap<>();
            tokenResponse.put("id_token", idToken);
            writeResponse(t, JsonUtil.toIndentedJson(tokenResponse).getBytes());
        });
        server.start();

        final String issuer = "http://localhost:" + server.getAddress().getPort();

        OpenIdConfiguration configuration = new OpenIdConfiguration(issuer, "ex.client", "ex.secret");

        String code = OpenIdConfiguration.randomString(40);
        Map<String, Object> idToken = configuration.fetchIdToken(code, "http://example.com/oauth2callback");
        assertEquals(subject, idToken.get("sub"));
    }

    void writeResponse(HttpExchange t, byte[] responseBody) throws IOException {
        t.sendResponseHeaders(200, responseBody.length);
        t.getResponseBody().write(responseBody);
        t.close();
    }
}