package org.logevents.extend.servlets;

import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.observers.InMemoryBufferLogEventObserver;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A servlet that exposes log information to administrative users. To use, you need:
 * <ul>
 *     <li>
 *         To run your application in a servlet container. Add LogEventsServlet as a servlet in <code>web.xml</code>,
 *         or add in a ServletContextListener
 *     </li>
 *     <li>
 *         You need an Identity Provider that supports OpenID Connect to authorize administrative users.
 *         If you don't have any existing options, I suggest creating a (free!) Azure Active Directory
 *         and adding users that should have access as guest users.
 *     </li>
 *     <li>
 *         In order to run LogEventsServlet needs security configuration in your logevents*.properties.
 *         You need to set <code>observer.servlet.openIdIssuer</code>, <code>observer.servlet.clientId</code>
 *         and <code>observer.servlet.clientSecret</code>.
 *     </li>
 *     <li>
 *         If you mount LogEventsServlet on "/logs", the API will be at "/logs/events", the OpenAPI documentation
 *         will be at "/logs/swagger.json" and a simple client web page will be at "/logs/"".
 *     </li>
 * </ul>
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

    private final static Logger logger = LoggerFactory.getLogger(LogEventsServlet.class);
    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    private LogEventFormatter formatter = new TTLLEventLogFormatter();

    private InMemoryBufferLogEventObserver observer = new InMemoryBufferLogEventObserver();
    private String logeventsHtml = "/org/logevents/logevents.html";
    private String logeventsApi = "/org/logevents/swagger.json";
    private Optional<String> redirectUri;
    private String clientId;
    private String clientSecret;
    private String openIdIssuer;
    private Optional<String> scopes;
    private Cipher encryptCipher;
    private Cipher decryptCipher;

    @Override
    public void init() {
        LogEventFactory.getInstance().addRootObserver(observer); // TODO: Must be persistent after reload!
        Properties properties = LogEventFactory.getInstance().getConfigurators().get(0).loadConfigurationProperties();
        configure(new Configuration(properties, "observer.servlet"));
        setupEncryption();
    }

    public void configure(Configuration configuration) {
        this.redirectUri = configuration.optionalString("redirectUri");
        this.clientId = configuration.getString("clientId");
        this.clientSecret = configuration.getString("clientSecret");
        this.openIdIssuer = configuration.getString("openIdIssuer");
        this.scopes = configuration.optionalString("scopes");
        configuration.checkForUnknownFields();
    }

    void setupEncryption() {
        String secretKey = randomString(40);
        try {
            SecretKeySpec keySpec=new SecretKeySpec(secretKey.getBytes(),"Blowfish");
            encryptCipher = Cipher.getInstance("Blowfish");
            encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec);
            decryptCipher = Cipher.getInstance("Blowfish");
            decryptCipher.init(Cipher.DECRYPT_MODE, keySpec);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
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
            resp.sendRedirect(req.getContextPath() + req.getServletPath() + "/" +
                    (req.getQueryString() != null ? "?" + req.getQueryString() : ""));
        } else if (req.getPathInfo().equals("/")) {
            if (req.getParameter("time") == null && req.getParameter("instant") != null) {
                Instant instant = Instant.parse(req.getParameter("instant"));
                LocalTime time = instant.atZone(ZoneId.systemDefault()).toLocalTime();
                LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();

                resp.sendRedirect(req.getContextPath() + req.getServletPath() + "/?"
                    + "time=" + time + "&date=" + date + "&" + req.getQueryString());
                return;
            }


            resp.setContentType("text/html");
            copyResource(resp, logeventsHtml);
        } else if (req.getPathInfo().equals("/swagger.json")) {
            resp.setContentType("application/json");
            Map<String, Object> api = (Map<String, Object>) JsonParser.parse(getClass().getResourceAsStream(logeventsApi));
            HashMap<Object, Object> localServer = new HashMap<>();
            localServer.put("url", req.getContextPath() + req.getServletPath());
            api.put("servers", Collections.singletonList(localServer));
            resp.getWriter().write(JsonUtil.toIndentedJson(api));
        } else if (req.getPathInfo().equals("/login")) {
            String state = randomString(50);
            resp.sendRedirect(getAuthorizationUrl(state, req));
        } else if (req.getPathInfo().equals("/oauth2callback")) {
            if (req.getParameter("error_description") != null) {
                resp.getWriter().write("Login failed\n\n");
                resp.getWriter().write(req.getParameter("error_description"));
                return;
            }

            Map<String, Object> idToken = fetchIdToken(req.getParameter("code"), getServletUrl(req) + "/oauth2callback");

            logger.warn(AUDIT, "User logged in {}", idToken);
            LogEventStatus.getInstance().addInfo(this, "User logged in " + idToken);

            resp.addCookie(createSessionCookie(idToken));

            resp.sendRedirect(req.getContextPath() + req.getServletPath());
        } else if (!authenticated(resp, req.getCookies())) {
            resp.sendError(401, "Please log in");
        } else if (req.getPathInfo().equals("/events")) {

            LogEventFilter filter = new LogEventFilter(req.getParameterMap());
            Collection<LogEvent> allEvents = filter.collectMessages(observer);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("facets", filter.collectFacets(allEvents));
            result.put("events", findEvents(filter, allEvents));

            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        } else {
            resp.sendError(404, "Not found " + req.getPathInfo());
        }
    }

    private String getServletUrl(HttpServletRequest req) {
        return getServerUrl(req) + req.getContextPath() + req.getServletPath();
    }

    Cookie createSessionCookie(Map<String, Object> idToken) {
        String session = "subject=" + idToken.get("sub") + "\n"
                + "sessionTime=" + Instant.ofEpochSecond(Long.parseLong(idToken.get("iat").toString()));
        return new Cookie("logevents.session", encrypt(session));
    }

    private Map<String, Object> fetchIdToken(String code, String defaultRedirectUri) throws IOException {
        Map<String, String> formPayload = new HashMap<>();
        formPayload.put("client_id", getClientId());
        formPayload.put("client_secret", getClientSecret());
        formPayload.put("redirect_uri", getRedirectUri(defaultRedirectUri));
        formPayload.put("grant_type", "authorization_code");
        formPayload.put("code", code);

        Map<String, Object> response = NetUtils.postFormForJson(getTokenUri(), formPayload);
        return getIdToken(response);
    }

    private List<Map<String, Object>> findEvents(LogEventFilter filter, Collection<LogEvent> allEvents) {
        return allEvents.stream()
                .filter(filter)
                .map(this::formatAsJson)
                .collect(Collectors.toList());
    }

    private String encrypt(String session) {
        try {
            return Base64.getEncoder().encodeToString(encryptCipher.doFinal(session.getBytes()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private String decrypt(String value) throws BadPaddingException, IllegalBlockSizeException {
        return new String(decryptCipher.doFinal(Base64.getDecoder().decode(value)));
    }


    private Map<String, Object> getIdToken(Map<String, Object> response) throws IOException {
        String idToken = response.get("id_token").toString();
        return (Map<String, Object>) JsonParser.parseFromBase64encodedString(idToken.split("\\.")[1]);
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
                "&redirect_uri=" + getRedirectUri(getServletUrl(req) + "/oauth2callback") +
                "&scope=" + getScope() +
                "&state=" + state;
    }

    private String getScope() {
        return scopes.orElse("openid+email+profile");
    }

    private String getRedirectUri(String defaultValue) {
        return redirectUri.orElse(defaultValue);
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

    boolean authenticated(HttpServletResponse resp, Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("logevents.session")) {
                    try {
                        Map<String, String> session = Stream.of(decrypt(cookie.getValue()).split("\n"))
                                .collect(Collectors.toMap(
                                        s -> s.split("=")[0],
                                        s -> s.split("=")[1]
                                ));
                        if (session.containsKey("sessionTime")) {
                            Instant sessionTime = Instant.parse(session.get("sessionTime"));
                            if (Instant.now().isBefore(sessionTime.plusSeconds(60*60))) {
                                return true;
                            }
                        }
                    } catch (GeneralSecurityException|IllegalArgumentException e) {
                        LogEventStatus.getInstance().addError(this, "Decoding session failed", e);
                    }
                    cookie.setValue("");
                    cookie.setMaxAge(0);
                    resp.addCookie(cookie);
                    return false;
                }
            }
        }
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

    Map<String, Object> formatAsJson(LogEvent event) {
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

    protected LogEventObserver getObserver() {
        return observer;
    }
}
