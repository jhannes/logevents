package org.logevents.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.LogEventBuffer;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.openid.OpenIdConfiguration;
import org.mockito.Mockito;
import org.slf4j.MarkerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogEventHttpServerTest {

    private HttpExchange mockExchange = Mockito.mock(HttpExchange.class);
    private ByteArrayOutputStream output = new ByteArrayOutputStream();
    private LogEventHttpServer logEventHttpServer = new LogEventHttpServer();
    private Headers requestHeaders = new Headers();
    private LogEventBuffer logEventSource = new LogEventBuffer();
    private Headers responseHeaders = new Headers();

    @Before
    public void setUp() {
        logEventHttpServer.setupCookieVault();
        logEventHttpServer.setLogEventSource(logEventSource);

        when(mockExchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(mockExchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(mockExchange.getResponseBody()).thenReturn(output);
    }

    @Test
    public void shouldServerApiSpec() throws IOException, URISyntaxException {
        when(mockExchange.getRequestURI()).thenReturn(new URI(
                "http://localhost/logs/swagger.json"));

        logEventHttpServer.httpHandler(mockExchange);
        Map<String, Object> openApiDefinition = JsonParser.parseObject(output.toString());

        Mockito.verify(mockExchange).sendResponseHeaders(eq(200), anyLong());
        Mockito.verify(mockExchange).getRequestURI();
        Mockito.verify(mockExchange).getRequestHeaders();
        Mockito.verify(mockExchange).getResponseHeaders();
        Mockito.verify(mockExchange).getResponseBody();
        Mockito.verifyNoMoreInteractions(mockExchange);

        assertEquals("Log Events - a simple Java Logging library",
                JsonUtil.getField(JsonUtil.getObject(openApiDefinition, "info"), "description"));
    }

    @Test
    public void shouldReturnLogEventFacets() throws IOException, URISyntaxException {
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("sessionTime", Instant.now().toString());
        String encrypted = logEventHttpServer.getSessionVault().encrypt(JsonUtil.toIndentedJson(sessionData));
        requestHeaders.set("Cookie", "logevents.session=" + encrypted);

        when(mockExchange.getRequestURI()).thenReturn(new URI("http://localhost/logs/events"));

        logEventHttpServer.httpHandler(mockExchange);
        Map<String, Object> events = JsonParser.parseObject(output.toString());
        assertEquals(new HashSet<>(Arrays.asList("nodes", "loggers", "threads", "markers", "mdc", "applications")),
                ((Map<String, Object>)events.get("facets")).keySet());
    }

    @Test
    public void shouldFilterLogEvents() throws URISyntaxException, IOException {
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("sessionTime", Instant.now().toString());
        String encrypted = logEventHttpServer.getSessionVault().encrypt(JsonUtil.toIndentedJson(sessionData));
        requestHeaders.set("Cookie", "logevents.session=" + encrypted);

        LogEvent includedEvent = new LogEventSampler().withMarker(MarkerFactory.getMarker("TEST")).build();
        logEventSource.logEvent(includedEvent);
        LogEvent excludedEvent = new LogEventSampler().withMarker().build();
        logEventSource.logEvent(excludedEvent);

        when(mockExchange.getRequestURI()).thenReturn(new URI("http://localhost/logs/events?a=b&marker=DUMMY&marker=TEST&foo=bar"));

        logEventHttpServer.httpHandler(mockExchange);

        Map<String, Object> events = JsonParser.parseObject(output.toString());
        List<Object> messages = ((List<Map<String, Object>>) events.get("events"))
                .stream()
                .map(m -> m.get("messageTemplate"))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(includedEvent.getMessage()), messages);
    }

    @Test
    public void shouldRequireAuthorizationForApiCalls() throws IOException, URISyntaxException {
        when(mockExchange.getRequestURI()).thenReturn(new URI("http://localhost/logs/events"));
        logEventHttpServer.httpHandler(mockExchange);

        verify(mockExchange).sendResponseHeaders(eq(401), anyLong());
        assertEquals("Please log in", output.toString());
    }

    @Test
    public void shouldCreateSessionOnOpenIdConnectCallback() throws IOException, URISyntaxException, GeneralSecurityException {
        when(mockExchange.getRequestURI()).thenReturn(new URI("http://localhost/logs/oauth2callback?code=abcd"));
        long iat = System.currentTimeMillis() / 1000;
        OpenIdConfiguration openIdConfiguration = new OpenIdConfiguration(null, null, null) {
            @Override
            protected Map<String, Object> postTokenRequest(Map<String, String> formPayload) {
                HashMap<String, Object> tokenResponse = new HashMap<>();
                Map<String, Object> idToken = new HashMap<>();
                idToken.put("iat", iat);
                idToken.put("email_verified", true);
                idToken.put("email", "bob@example.com");
                String payload = Base64.getEncoder().encodeToString(JsonUtil.toIndentedJson(idToken).getBytes());
                tokenResponse.put("id_token", "sdgslnl." + payload + ".dgs");
                return tokenResponse;
            }
        };
        openIdConfiguration.addRequiredClaim("email", Arrays.asList("alice@example.com", "bob@example.com"));
        logEventHttpServer.setOpenIdConfiguration(openIdConfiguration);

        logEventHttpServer.httpHandler(mockExchange);
        verify(mockExchange).sendResponseHeaders(eq(302), anyLong());

        String cookie = responseHeaders.getFirst("Set-Cookie");
        int equalsPos = cookie.indexOf('=');
        assertEquals("logevents.session", cookie.substring(0, equalsPos));
        int semiPos = cookie.indexOf(';', equalsPos);
        if (semiPos < 0) semiPos = cookie.length();

        String cookieValue = cookie.substring(equalsPos + 1, semiPos);
        Map<String, Object> sessionInfo = JsonParser.parseObject(logEventHttpServer.getSessionVault().decrypt(cookieValue));
        assertEquals(Instant.ofEpochSecond(iat),
                Instant.parse(sessionInfo.get("sessionTime").toString()));
    }

    @Test
    public void shouldRejectIdTokensWithoutRequiredClaims() throws IOException, URISyntaxException {
        when(mockExchange.getRequestURI()).thenReturn(new URI("http://localhost/logs/oauth2callback?code=abcd"));
        OpenIdConfiguration openIdConfiguration = new OpenIdConfiguration(null, null, null) {
            @Override
            protected Map<String, Object> postTokenRequest(Map<String, String> formPayload) {
                HashMap<String, Object> tokenResponse = new HashMap<>();
                Map<String, Object> idToken = new HashMap<>();
                idToken.put("iat", System.currentTimeMillis() / 1000);
                idToken.put("email", "stranger@example.org");
                idToken.put("email_verified", true);
                String payload = Base64.getEncoder().encodeToString(JsonUtil.toIndentedJson(idToken).getBytes());
                tokenResponse.put("id_token", "sdgslnl." + payload + ".dgs");
                return tokenResponse;
            }
        };
        openIdConfiguration.addRequiredClaim("email_verified", Arrays.asList("true"));
        openIdConfiguration.addRequiredClaim("email", Arrays.asList("johannes@example.org", "user@example.org"));
        logEventHttpServer.setOpenIdConfiguration(openIdConfiguration);

        logEventHttpServer.httpHandler(mockExchange);

        verify(mockExchange).sendResponseHeaders(eq(403), anyLong());
        assertNull(responseHeaders.getFirst("Set-Cookie"));
    }

}