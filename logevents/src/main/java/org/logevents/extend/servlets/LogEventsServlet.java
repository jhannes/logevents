package org.logevents.extend.servlets;

import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.logevents.util.Configuration;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Example configuration:
 *
 * <pre>
 * observer.servlet.openIdIssuer=https://login.microsoftonline.com/common
 * observer.servlet.clientId=12345678-abcd-pqrs-9876-9abcdef01234
 * observer.servlet.clientSecret=3¤..¤!?qwer
 * observer.servlet.redirectUri=https://my-server.example.com/logs/oauth2callback
 * observer.servlet.requiredClaim.username=johannes@brodwall.com,someone@brodwall.com
 * observer.servlet.requiredClaim.roles=admin
 * </pre>
 */
public class LogEventsServlet extends HttpServlet {

    private LogEventFormatter formatter = new TTLLEventLogFormatter();

    private Map<Level, CircularBufferLogEventObserver> messages = new HashMap<>();
    private String logeventsHtml = "/org/logevents/logevents.html";
    private String logeventsApi = "/org/logevents/swagger.json";
    private Optional<String> redirectUri;
    private String clientId;
    private String clientSecret;
    private String openIdIssuer;
    private Optional<String> scopes;

    @Override
    public void init(ServletConfig config) {
        attachLogEventObservers(LogEventFactory.getInstance());
        Properties properties = LogEventFactory.getInstance().getConfigurators().get(0).loadConfigurationProperties();
        Configuration configuration = new Configuration(properties, "observer.servlet");
        this.redirectUri = configuration.optionalString("redirectUri");
        this.clientId = configuration.getString("clientId");
        this.clientSecret = configuration.getString("clientSecret");
        this.openIdIssuer = configuration.getString("openIdIssuer");
        this.scopes = configuration.optionalString("scopes");
        configuration.checkForUnknownFields();
    }

    void attachLogEventObservers(LogEventFactory factory) {
        for (Level level : Level.values()) {
            CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
            this.messages.put(level, observer);
            factory.addRootObserver(levelObserver(observer, level));
        }
    }

    private LogEventObserver levelObserver(LogEventObserver delegate, Level level) {
        return new LogEventObserver() {
            @Override
            public void logEvent(LogEvent e) {
                if (e.getLevel().equals(level)) {
                    delegate.logEvent(e);
                }
            }

            @Override
            public String toString() {
                return "LevelConditionalObserver{level=" + level + ",delegate=" + delegate + "}";
            }
        };
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        if (req.getPathInfo() == null) {
            resp.setContentType("text/html");
            copyResource(resp, logeventsHtml);
        } else if (req.getPathInfo().equals("/swagger.json")) {
            resp.setContentType("application/json");
            copyResource(resp, logeventsApi);
        } else if (req.getPathInfo().equals("/login")) {
            String state = randomString(50);
            resp.sendRedirect(getAuthorizationUrl(state, req));
        } else if (req.getPathInfo().equals("/oauth2callback")) {
            if (req.getParameter("error_description") != null) {
                resp.getWriter().write("Login failed\n\n");
                resp.getWriter().write(req.getParameter("error_description"));
                return;
            }

            Map<String, String> formPayload = new HashMap<>();
            formPayload.put("client_id", getClientId());
            formPayload.put("client_secret", getClientSecret());
            formPayload.put("redirect_uri", getRedirectUri(req));
            formPayload.put("grant_type", "authorization_code");
            formPayload.put("code", req.getParameter("code"));

            Map<String, Object> response = NetUtils.postFormForJson(getTokenUri(), formPayload);
            String idToken = response.get("id_token").toString();
            resp.getWriter().write(JsonParser.parseFromBase64encodedString(idToken.split("\\.")[1]).toString());
        } else if (!authenticated(req)) {
            resp.sendError(401, "Please log in");
        } else if (req.getPathInfo().equals("/events") || req.getPathInfo().equals("/logs/events")) {

            LogEventFilter filter = new LogEventFilter(req.getParameterMap());

            Collection<LogEvent> allEvents = filter.collectMessages(messages);
            List<Map<String, Object>> events = allEvents.stream()
                    .filter(filter)
                    .map(this::formatAsJson)
                    .collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("facets", filter.collectFacets(allEvents));
            result.put("events", events);

            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        } else {
            Map<String, Object> result = getLogEvents();
            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        }
    }

    private URL getTokenUri() throws IOException {
        return new URL((String) loadOpenIdConfiguration().get("token_endpoint"));
    }

    private String getClientSecret() {
        return clientSecret;
    }

    private String getAuthorizationUrl(String state, HttpServletRequest req) throws IOException {
        return getAuthorizationEndpoint() + "?" +
                "response_type=code" +
                "&client_id=" + getClientId() +
                "&redirect_uri=" + getRedirectUri(req) +
                "&scope=" + getScope() +
                "&state=" + state;
    }

    private String getScope() {
        return scopes.orElse("openid+email+profile");
    }

    private String getRedirectUri(HttpServletRequest req) {
        return redirectUri.orElseGet(() ->
            getServerUrl(req) + req.getContextPath() + req.getServletPath() + "/oauth2callback"
        );
    }

    private String getClientId() {
        return clientId;
    }

    private String getAuthorizationEndpoint() throws IOException {
        return (String) loadOpenIdConfiguration().get("authorization_endpoint");
    }

    private Map<String, Object> loadOpenIdConfiguration() throws IOException {
        return (Map<String, Object>)
                JsonParser.parse(new URL(this.openIdIssuer + "/.well-known/openid-configuration"));
    }

    private static final Random random = new Random();

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";


    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private boolean authenticated(HttpServletRequest req) {
        return false;
    }

    private void copyResource(HttpServletResponse resp, String resource) throws IOException {
        try (InputStream html = getClass().getResourceAsStream(resource)) {
            int c;
            while ((c = html.read()) != -1) {
                resp.getOutputStream().write((byte) c);
            }
        }
    }

    private Map<String, Object> formatAsJson(LogEvent event) {
        Map<String, Object> jsonEvent = new HashMap<>();

        jsonEvent.put("thread", event.getThreadName());
        jsonEvent.put("time", event.getInstant().toString());
        jsonEvent.put("logger", event.getLoggerName());
        jsonEvent.put("abbreviatedLogger", event.getAbbreviatedLoggerName(0));
        jsonEvent.put("level", event.getLevel().name());
        jsonEvent.put("levelIcon", JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()));
        jsonEvent.put("formattedMessage", event.formatMessage());
        jsonEvent.put("messageTemplate", event.getMessage());
        jsonEvent.put("marker", Optional.ofNullable(event.getMarker()).map(Marker::getName).orElse(null));

        if (event.getThrowable() != null) {
            jsonEvent.put("throwable", event.getThrowable().toString());
            ArrayList<Map<String, Object>> stackTrace = new ArrayList<>();
            for (StackTraceElement element : event.getThrowable().getStackTrace()) {
                Map<String, Object> jsonElement = new HashMap<>();
                jsonElement.put("className", element.getClassName());
                jsonElement.put("methodName", element.getMethodName());
                jsonElement.put("lineNumber", String.valueOf(element.getLineNumber()));
                jsonElement.put("fileName", element.getFileName());
                stackTrace.add(jsonElement);
            }
            jsonEvent.put("stackTrace", stackTrace);
        }

        List<Object> mdc = new ArrayList<>();
        for (Map.Entry<String, String> entry : event.getMdcProperties().entrySet()) {
            Map<String, String> mdcEntry = new HashMap<>();
            mdcEntry.put("name", entry.getKey());
            mdcEntry.put("value", entry.getValue());
            mdc.add(mdcEntry);
        }
        jsonEvent.put("mdc", mdc);
        return jsonEvent;
    }

    Map<String, Object> getLogEvents() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<Level, CircularBufferLogEventObserver> entry : messages.entrySet()) {
            result.put(entry.getKey().name().toLowerCase(), convert(entry.getValue().getEvents()));
        }
        return result;
    }

    private List<String> convert(Collection<LogEvent> events) {
        ArrayList<String> result = new ArrayList<>();
        for (LogEvent event : events) {
            result.add(formatter.apply(event));
        }
        return result;
    }

    private String getServerUrl(HttpServletRequest req) {
        String scheme = Optional.ofNullable(req.getHeader("X-Forwarded-Proto")).orElse(req.getScheme());
        int port = Optional.ofNullable(req.getHeader("X-Forwarded-Port")).map(Integer::parseInt).orElse(req.getServerPort());
        String host = req.getServerName();
        int defaultSchemePort = scheme.equals("https") ? 443 : 80;

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(host);
        if (port != defaultSchemePort) {
            url.append(":").append(port);
        }
        return url.toString();
    }
}
