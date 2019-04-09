package org.logevents.extend.servlets;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.observers.LogEventBuffer;
import org.logevents.util.Configuration;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogEventsServletTest extends LogEventsServlet {

    private LogEventsServlet servlet = new LogEventsServlet();
    private Logger logger = LogEventFactory.getInstance().getLogger(getClass().getName());

    @Before
    public void initServlet() {
        servlet.setupEncryption(Optional.empty());
    }

    @Test
    public void usersShouldBeAuthenticated() {
        HashMap<String, Object> idToken = new HashMap<>();
        idToken.put("sub", "subjectId12345_abc");
        idToken.put("iat", System.currentTimeMillis());
        Cookie sessionCookie = servlet.createSessionCookie(idToken);

        boolean authenticated = servlet.authenticated(Mockito.mock(HttpServletResponse.class),
                new Cookie[]{sessionCookie}
        );
        assertTrue(sessionCookie + " should be authenticated", authenticated);
    }

    @Test
    public void shouldRemoveExpiredCookie() {
        Cookie sessionCookie = createSessionCookie(Instant.now().minusSeconds(2 * 60 * 60));
        assertEquals(-1, sessionCookie.getMaxAge());

        HttpServletResponse responseMock = Mockito.mock(HttpServletResponse.class);
        boolean authenticated = servlet.authenticated(responseMock, new Cookie[] { sessionCookie });
        assertFalse(sessionCookie + " should be expired", authenticated);
        verify(responseMock).addCookie(sessionCookie);
        assertEquals(0, sessionCookie.getMaxAge());
    }

    @Test
    public void shouldRemoveTamperedCookie() {
        Cookie sessionCookie = createSessionCookie(Instant.now().minusSeconds(2 * 60 * 60));
        sessionCookie.setValue(sessionCookie.getValue()+"0");

        HttpServletResponse responseMock = Mockito.mock(HttpServletResponse.class);
        boolean authenticated = servlet.authenticated(responseMock, new Cookie[] { sessionCookie });
        assertFalse(sessionCookie + " should be expired", authenticated);
        verify(responseMock).addCookie(sessionCookie);
    }

    @Test
    public void shouldFormatLogEvent() throws IOException {
        LogEventBuffer observer = new LogEventBuffer();
        LogEventFactory.getInstance().setRootObserver(observer);
        LogEvent logEvent = new LogEventSampler().withMarker().withMdc("clientIp", "127.0.0.1")
                .withThrowable(new IOException()).build();
        observer.logEvent(logEvent);

        servlet.setLogEventBuffer(observer);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("/events");
        when(request.getCookies()).thenReturn(new Cookie[] { createSessionCookie(Instant.now()) });
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter result = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(result));
        servlet.doGet(request, response);

        verify(response).setContentType("application/json");
        Map<String, Object> object = (Map<String, Object>) JsonParser.parse(result.toString());

        List<Object> events = JsonUtil.getList(object, "events");
        Object classNameOfStacktrace = JsonUtil.getField(
                JsonUtil.getObject(
                        JsonUtil.getList(JsonUtil.getObject(events, 0), "stackTrace"),
                0), "className");
        assertEquals(getClass().getName(), classNameOfStacktrace);
    }

    @Test
    public void shouldGenerateAuthenticationUrl() throws IOException {
        Properties properties = new Properties();
        properties.put("observer.servlet.clientId", "my-application");
        properties.put("observer.servlet.clientSecret", "abc123");
        properties.put("observer.servlet.openIdIssuer", "https://login.microsoftonline.com/common");
        servlet.configure(new Configuration(properties, "observer.servlet"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("/login");
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("www.example.com");
        when(request.getContextPath()).thenReturn("");
        when(request.getServletPath()).thenReturn("/logs");
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(request, response);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(captor.capture());

        String prefix = "https://login.microsoftonline.com/common/oauth2/authorize?response_type=code&client_id=my-application&redirect_uri=https://www.example.com";
        assertTrue(captor.getValue() + " should start with " + prefix,
                captor.getValue().startsWith(prefix));
    }

    @Test
    public void shouldReturnOpenApiDefinition() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("/swagger.json");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));

        servlet.doGet(request, response);
        verify(response).setContentType("application/json");
        Map<String, Object> openApiDefinition = (Map<String, Object>) JsonParser.parse(output.toString());


        assertEquals("Log Events - a simple Java Logging library",
                JsonUtil.getField(JsonUtil.getObject(openApiDefinition, "info"), "description"));


    }

    public Cookie createSessionCookie(Instant now) {
        HashMap<String, Object> idToken = new HashMap<>();
        idToken.put("sub", "subjectId12345_abc");
        idToken.put("iat", now.getEpochSecond());
        return servlet.createSessionCookie(idToken);
    }
}
