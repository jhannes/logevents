package org.logevents.extend.servlets;

import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.observers.WebLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.LogEventConfigurationException;
import org.logevents.util.openid.OpenIdConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A servlet that exposes log information to administrative users via a built in web page. To use, you need to:
 * <ul>
 *     <li>
 *         Run your application in a servlet container: Add LogEventsServlet as a servlet in <code>web.xml</code>, add in a ServletContextListener or in Spring, add a <code>ServletRegistrationBean</code>
 *     </li>
 *     <li>
 *         You need an Identity Provider that supports OpenID Connect to authorize administrative users.
 *         If you don't have any existing options, I suggest creating a (free!) Azure Active Directory
 *         and adding users that should have access as guest users. See {@link OpenIdConfiguration}
 *         to learn how to set this up.
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
 *
 * <h2>Example configuration:</h2>
 *
 * <pre>
 * observer.servlet=WebLogEventObserver
 * observer.servlet.openIdIssuer=https://login.microsoftonline.com/common
 * observer.servlet.clientId=12345678-abcd-pqrs-9876-9abcdef01234
 * observer.servlet.clientSecret=3¤..¤!?qwer
 * observer.servlet.redirectUri=https://my-server.example.com/logs/oauth2callback
 * observer.servlet.requiredClaim.username=johannes@brodwall.com,someone@brodwall.com
 * observer.servlet.requiredClaim.roles=admin
 * </pre>
 *
 * <h2>Register LogEventsServlet in your servlet container</h2>
 *
 * <h3>Example web.xml-file</h3>
 *
 * <pre>
 * &lt;servlet&gt;
 *     &lt;servlet-name&gt;LogEvents&lt;/servlet-name&gt;
 *     &lt;servlet-class&gt;org.logevents.extend.servlets.LogEventsServlet&lt;/servlet-class&gt;
 * &lt;/servlet&gt;
 * &lt;servlet-mapping&gt;
 *   &lt;servlet-name&gt;LogEvents&lt;/servlet-name&gt;
 *   &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 * &lt;/servlet-mapping&gt;
 * </pre>
 *
 * <h3>Example ServletContextListener</h3>
 *
 * <pre>
 * public class ApplicationContext implements ServletContextListener {
 *     public void contextInitialized(ServletContextEvent sce) {
 *        sce.getServletContext().addServlet("logs", new LogEventsServlet()).addMapping("/logs/*");
 *    }
 *    public void contextDestroyed(ServletContextEvent sce) {
 *    }
 * }
 * </pre>
 *
 * <h3>Example Spring ServletRegistrationBean</h3>
 *
 * <pre>
 * &#064;Bean
 * public ServletRegistrationBean servletRegistrationBean(){
 *     return new ServletRegistrationBean(new LogEventsServlet(), "/logs/*");
 * }
 * </pre>
 *
 * @see WebLogEventObserver
 * @see OpenIdConfiguration
 * @see LogEventFilter
 *
 */
public class LogEventsServlet extends HttpServlet {

    private final static Logger logger = LoggerFactory.getLogger(LogEventsServlet.class);
    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");
    private static final String LOGEVENTS_API = "/org/logevents/swagger.json";

    private Cipher encryptCipher;
    private Cipher decryptCipher;

    @Override
    public void init() throws ServletException {
        setupEncryption(getObserver().getCookieEncryptionKey());
    }

    void setupEncryption(Optional<String> cookieEncryptionKey) {
        try {
            SecretKeySpec keySpec=new SecretKeySpec(
                    cookieEncryptionKey.orElseGet(() -> randomString(40)).getBytes(),
                    "Blowfish"
            );
            encryptCipher = Cipher.getInstance("Blowfish");
            encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec);
            decryptCipher = Cipher.getInstance("Blowfish");
            decryptCipher.init(Cipher.DECRYPT_MODE, keySpec);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
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
            copyResource(resp, getObserver().getLogEventsHtml());
        } else if (req.getPathInfo().equals("/swagger.json")) {
            resp.setContentType("application/json");
            Map<String, Object> api = JsonParser.parseObject(getClass().getResourceAsStream(LOGEVENTS_API));
            HashMap<Object, Object> localServer = new HashMap<>();
            localServer.put("url", req.getContextPath() + req.getServletPath());
            api.put("servers", Collections.singletonList(localServer));
            resp.getWriter().write(JsonUtil.toIndentedJson(api));
        } else if (req.getPathInfo().equals("/login")) {
            String state = randomString(50);
            Cookie cookie = new Cookie("logevents.query", req.getQueryString());
            cookie.setMaxAge(300);
            resp.addCookie(cookie);
            resp.sendRedirect(getOpenIdConfiguration()
                    .getAuthorizationUrl(state, getServletUrl(req)));
        } else if (req.getPathInfo().equals("/oauth2callback")) {
            if (req.getParameter("error_description") != null) {
                resp.getWriter().write("Login failed\n\n");
                resp.getWriter().write(req.getParameter("error_description"));
                return;
            }

            Map<String, Object> idToken = getOpenIdConfiguration()
                    .fetchIdToken(req.getParameter("code"), getServletUrl(req) + "/oauth2callback");

            logger.warn(AUDIT, "User logged in {}", idToken);
            LogEventStatus.getInstance().addInfo(this, "User logged in " + idToken);

            resp.addCookie(createSessionCookie(idToken));
            String location = req.getContextPath() + req.getServletPath() + "/";
            String redirectTo = findCookie(req.getCookies(), "logevents.query")
                .map(query -> location + "?" + query)
                .orElse(location);
            resp.sendRedirect(redirectTo);
        } else if (!authenticated(resp, req.getCookies())) {
            resp.sendError(401, "Please log in");
        } else if (req.getPathInfo().equals("/events")) {
            LogEventFilter filter = new LogEventFilter(req.getParameterMap());
            Collection<LogEvent> allEvents = filter.collectMessages(getObserver().getLogEventBuffer());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("facets", filter.collectFacets(allEvents));
            result.put("events", allEvents.stream()
                    .filter(filter)
                    .map(getObserver()::format)
                    .collect(Collectors.toList()));

            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toIndentedJson(result));
        } else {
            resp.sendError(404, "Not found " + req.getPathInfo());
        }
    }

    protected OpenIdConfiguration getOpenIdConfiguration() throws ServletException {
        return getObserver().getOpenIdConfiguration();
    }

    private Optional<String> findCookie(Cookie[] reqCookies, String name) {
        return Optional.ofNullable(reqCookies)
                .flatMap(cookies -> Stream.of(cookies)
                        .filter(c -> c.getName().equals(name))
                        .map(Cookie::getValue)
                        .findAny()
                );
    }

    private String getServletUrl(HttpServletRequest req) {
        return getServerUrl(req) + req.getContextPath() + req.getServletPath();
    }

    Cookie createSessionCookie(Map<String, Object> idToken) {
        String session = "subject=" + idToken.get("sub") + "\n"
                + "sessionTime=" + Instant.ofEpochSecond(Long.parseLong(idToken.get("iat").toString()));
        return new Cookie("logevents.session", encrypt(session));
    }


    private String encrypt(String session) {
        try {
            return Base64.getEncoder().encodeToString(encryptCipher.doFinal(session.getBytes()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    String decrypt(String value) throws BadPaddingException, IllegalBlockSizeException {
        return new String(decryptCipher.doFinal(Base64.getDecoder().decode(value)));
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
                    } catch (GeneralSecurityException|IllegalArgumentException|ArrayIndexOutOfBoundsException e) {
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
        try (Reader html = new InputStreamReader(getClass().getResourceAsStream(resource))) {
            int c;
            while ((c = html.read()) != -1) {
                resp.getWriter().write((char) c);
            }
        }
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

    public WebLogEventObserver getObserver() throws ServletException {
        try {
            return (WebLogEventObserver) LogEventFactory.getInstance().getObserver("servlet");
        } catch (LogEventConfigurationException e) {
            throw new ServletException("logevents.properties must contain observer.servlet=WebLogEventObserver to use " + this);
        }
    }

}
