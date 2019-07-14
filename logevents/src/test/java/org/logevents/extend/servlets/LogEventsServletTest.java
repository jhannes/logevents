package org.logevents.extend.servlets;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.observers.LogEventBuffer;
import org.logevents.observers.WebLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.openid.OpenIdConfiguration;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogEventsServletTest extends LogEventsServlet {

    private LogEventsServlet servlet = new LogEventsServlet();
    private Logger logger = LogEventFactory.getInstance().getLogger(getClass().getName());
    private Random random = new Random();
    private HttpServletResponse response = mock(HttpServletResponse.class);
    private HttpServletRequest request = mock(HttpServletRequest.class);

    @Before
    public void initServlet() throws ServletException {
        servlet.setupCookieVault(Optional.empty());
    }

    @Before
    public void setupRequest() {
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("www.example.com");
        when(request.getContextPath()).thenReturn("");
        when(request.getServletPath()).thenReturn("/logs");
        when(request.getHeader("Host")).thenReturn("www.example.com");
    }

    @Test
    public void usersShouldBeAuthenticated() {
        Map<String, Object> idToken = createSessionCookieToken(System.currentTimeMillis());
        Cookie sessionCookie = servlet.createSessionCookie(idToken);

        boolean authenticated = servlet.authenticated(response,
                new Cookie[]{sessionCookie}
        );
        assertTrue(sessionCookie + " should be authenticated", authenticated);
    }

    @Test
    public void shouldRemoveExpiredCookie() {
        Cookie sessionCookie = servlet.createSessionCookie(
                createSessionCookieToken(Instant.now().minusSeconds(2 * 60 * 60).getEpochSecond())
        );
        assertEquals(-1, sessionCookie.getMaxAge());

        boolean authenticated = servlet.authenticated(response, new Cookie[] { sessionCookie });
        assertFalse(sessionCookie + " should be expired", authenticated);
        verify(response).addCookie(sessionCookie);
        assertEquals(0, sessionCookie.getMaxAge());
    }

    @Test
    public void shouldRemoveTamperedCookie() {
        LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
        Cookie sessionCookie = servlet.createSessionCookie(
                createSessionCookieToken(Instant.now().minusSeconds(2 * 60 * 60).getEpochSecond())
        );
        sessionCookie.setValue("000" + sessionCookie.getValue().substring(3));

        boolean authenticated = servlet.authenticated(response, new Cookie[] { sessionCookie });
        assertFalse(sessionCookie + " should be expired", authenticated);
        verify(response).addCookie(sessionCookie);
    }

    @Test
    public void shouldFormatLogEvent() throws IOException, ServletException {
        LogEventBuffer buffer = new LogEventBuffer();
        WebLogEventObserver observer = new WebLogEventObserver() {
            @Override
            public LogEventBuffer getLogEventSource() {
                return buffer;
            }
        };
        HashMap<String, Supplier<? extends LogEventObserver>> observers = new HashMap<>();
        observers.put("servlet", () -> observer);
        LogEventFactory.getInstance().setObservers(observers);
        LogEvent logEvent = new LogEventSampler().withMarker().withMdc("clientIp", "127.0.0.1")
                .withThrowable(new IOException()).build();
        buffer.logEvent(logEvent);

        servlet.setupCookieVault(Optional.empty());

        when(request.getPathInfo()).thenReturn("/events");
        Map<String, Object> idToken = createSessionCookieToken(Instant.now().getEpochSecond());
        when(request.getCookies()).thenReturn(new Cookie[] { servlet.createSessionCookie(idToken)});

        StringWriter result = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(result));
        servlet.doGet(request, response);

        verify(response).setContentType("application/json");
        Map<String, Object> object = JsonParser.parseObject(result.toString());

        List<Object> events = JsonUtil.getList(object, "events");
        Object classNameOfStacktrace = JsonUtil.getField(
                JsonUtil.getObject(
                        JsonUtil.getList(JsonUtil.getObject(events, 0), "stackTrace"),
                0), "className");
        assertEquals(getClass().getName(), classNameOfStacktrace);
    }

    @Test
    @Ignore
    public void shouldGenerateAuthenticationUrl() throws IOException, ServletException {
        Properties properties = new Properties();
        properties.put("observer.servlet.clientId", "my-application");
        properties.put("observer.servlet.clientSecret", "abc123");
        properties.put("observer.servlet.openIdIssuer", "https://login.microsoftonline.com/common");
        WebLogEventObserver observer = new WebLogEventObserver(new Configuration(properties, "observer.servlet"));
        HashMap<String, Supplier<? extends LogEventObserver>> observers = new HashMap<>();
        observers.put("servlet", () -> observer);
        LogEventFactory.getInstance().setObservers(observers);
        servlet.setupCookieVault(Optional.empty());

        when(request.getPathInfo()).thenReturn("/login");

        servlet.doGet(request, response);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(captor.capture());

        String prefix = "https://login.microsoftonline.com/common/oauth2/authorize?response_type=code&client_id=my-application&redirect_uri=https://www.example.com";
        assertTrue(captor.getValue() + " should start with " + prefix,
                captor.getValue().startsWith(prefix));
    }

    @Test
    public void shouldCompleteLogin() throws IOException, GeneralSecurityException, ServletException {
        when(request.getPathInfo()).thenReturn("/oauth2callback");
        when(request.getParameter("code")).thenReturn(String.valueOf(random.nextInt()));

        Instant issueTime = ZonedDateTime.of(2019, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()).plusMinutes(random.nextInt(60 * 24 * 365)).toInstant();
        long subject = random.nextLong();
        OpenIdConfiguration openIdConfiguration = new OpenIdConfiguration(null, null, null) {
            @Override
            protected Map<String, Object> postTokenRequest(Map<String, String> formPayload) {
                HashMap<String, Object> tokenResponse = new HashMap<>();

                Map<String, Object> idToken = new HashMap<>();
                idToken.put("iat", issueTime.toEpochMilli()/1000);
                idToken.put("sub", subject);
                String payload = Base64.getEncoder().encodeToString(JsonUtil.toIndentedJson(idToken).getBytes());
                tokenResponse.put("id_token", "sdgslnl." + payload + ".dgs");
                return tokenResponse;
            }
        };

        LogEventsServlet servlet = new LogEventsServlet() {
            @Override
            protected OpenIdConfiguration getOpenIdConfiguration() {
                return openIdConfiguration;
            }
        };
        servlet.setupCookieVault(Optional.empty());

        servlet.doGet(request, response);

        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        String cookieValue = servlet.decrypt(cookieCaptor.getValue().getValue());

        assertEquals("subject=" + subject + "\nsessionTime=" + issueTime,
                cookieValue);
    }

    @Test
    public void shouldRejectIdTokensWithoutRequiredClaims() throws ServletException, IOException {
        Properties properties = new Properties();
        properties.put("observer.servlet.clientId", "my-application");
        properties.put("observer.servlet.clientSecret", "abc123");
        properties.put("observer.servlet.openIdIssuer", "https://login.microsoftonline.com/common");
        properties.put("observer.servlet.requiredClaim.email_verified", "true");
        properties.put("observer.servlet.requiredClaim.email", "alice@example.com, bob@example.com");

        OpenIdConfiguration openIdConfiguration = new OpenIdConfiguration(new Configuration(properties, "observer.servlet")) {
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
        LogEventsServlet servlet = new LogEventsServlet() {
            @Override
            protected OpenIdConfiguration getOpenIdConfiguration() {
                return openIdConfiguration;
            }
        };
        servlet.setupCookieVault(Optional.empty());

        when(request.getPathInfo()).thenReturn("/oauth2callback");
        when(request.getParameter("code")).thenReturn(String.valueOf(random.nextInt()));
        servlet.doGet(request, response);

        verify(response, never()).addCookie(any());
        verify(response).sendError(eq(403), anyString());
    }

    @Test
    public void shouldReturnOpenApiDefinition() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/swagger.json");

        StringWriter output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));

        servlet.doGet(request, response);
        verify(response).setContentType("application/json");
        Map<String, Object> openApiDefinition = JsonParser.parseObject(output.toString());

        assertEquals("Log Events - a simple Java Logging library",
                JsonUtil.getField(JsonUtil.getObject(openApiDefinition, "info"), "description"));
    }

    @Test
    @Ignore
    public void shouldReturnHtmlPage() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/");

        StringWriter output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));

        servlet.doGet(request, response);
        verify(response).setContentType("text/html");
        assertTrue("Should be an HTML file: " + output,
                output.toString().startsWith("<!DOCTYPE html>\n<html>"));
    }

    public Map<String, Object> createSessionCookieToken(long epochSecond) {
        HashMap<String, Object> idToken = new HashMap<>();
        idToken.put("sub", "subjectId12345_abc");
        idToken.put("iat", epochSecond);
        return idToken;
    }
}
