package org.logevents.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsServer;
import org.logevents.observers.LogEventSource;
import org.logevents.observers.WebLogEventObserver;
import org.logevents.query.LogEventFilter;
import org.logevents.query.LogEventQueryResult;
import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.openid.OpenIdConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// TODO state
// TODO observer...openIdRequire.email=jhannes@gmail.com,someone@example.com

/**
 * The simplest way to expose your logs in your web browser, LogEventHttpServer runs an embedded http server
 * in your application for the logs app console. Requires that you configure an {@link OpenIdConfiguration}
 * so you don't accidentally expose it insecurely. See {@link OpenIdConfiguration} for details on how to
 * set this up.
 *
 * <h2>Running with http (unencrypted)</h2>
 *
 * The simplest way to get {@link LogEventHttpServer} up an running is to use <code>http</code>. However
 * this will probably only work for localhost, because OpenID Connect providers generally only allow
 * apps to use http for localhost.
 *
 * <ol>
 *     <li><code>observer.web=WebLogEventServer</code></li>
 *     <li>Register a {@link WebLogEventObserver} with an <code>httpPort</code>.
 *     (E.g. <code>observer.web.httpPort=8080</code>)
 *     </li>
 *     <li>Setup a {@link OpenIdConfiguration} (e.g. <code>observer.web.openIdIssuer=https://login.microsoft.com</code>,
 *     <code>observer.web.clientId=...</code>, <code>observer.web.clientSecret=...</code>)
 *     </li>
 *     <li>Start your application with the configuration</li>
 *     <li>Open a web browser to e.g. <code>http://localhost:8080/logs</code>. You will now be logged in
 *     with your Open ID Connect provider and the see your logs</li>
 * </ol>
 *
 * This allows your to access you log from localhost. If you want remote access, you either have to put your
 * application behind an https reverse proxy or use the method below to enable https.
 *
 * <h2>Running with https</h2>
 *
 * In order to use an https-server, the server needs a certificate (and private key) that is trusted by
 * you web browser. This makes it a bit trickier than http.
 *
 * <ol>
 *     <li><code>observer.web=WebLogEventServer</code></li>
 *     <li>Register a {@link WebLogEventObserver} with an <code>httpsPort</code>.
 *     (E.g. <code>observer.web.httpsPort=8443</code>)
 *     </li>
 *     <li>Setup a {@link OpenIdConfiguration} (e.g. <code>observer.web.openIdIssuer=https://login.microsoft.com</code>,
 *     <code>observer.web.clientId=...</code>, <code>observer.web.clientSecret=...</code>)
 *     </li>
 *     <li>Start your application with the configuration</li>
 *     <li>LogEvents will produce a file named <code>key-<em>hostname</em>.crt</code>. You will need to
 *     install this as a Trusted CA Root in you operating system. Normally, you can do this by double clicking
 *     on the file, select Import and make sure you import it into "Trusted Root Certificates Authorities".
 *     Alternatively to trusting a new CA, you can procure a P12 certificate and key file from a CA and
 *     get LogEvents to use this (<code>observer.web.keyStore=my-certificate.p12</code>
 *     </li>
 *     <li>Open a web browser to e.g. <code>https://localhost:8443/logs</code>. You will now be logged in
 *     with your Open ID Connect provider and the see your logs</li>
 * </ol>
 *
 * <h2>Sample config</h2>
 *
 * <pre>
 * observer.web=WebLogEventObserver
 * observer.web.openIdIssuer=https://login.microsoftonline.com/common
 * observer.web.clientId=12345678-abcd-pqrs-9876-9abcdef01234
 * observer.web.clientSecret=3¤..¤!?qwer
 * observer.web.source=DatabaseLogEventObserver
 * observer.web.source.jdbcUrl=...
 * observer.web.httpsPort=8443
 * observer.web.keyStore=MyCertificate.p12
 * observer.web.keyStorePassword=mfldnlsnaa
 * observer.web.hostKeyPassword=2112wfsasa
 * </pre>
 *
 */
public class LogEventHttpServer extends AbstractLogEventHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(LogEventHttpServer.class);
    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    private String hostname = null;
    private Optional<Integer> httpPort = Optional.empty();
    private Optional<Integer> httpsPort = Optional.empty();
    private HttpServer httpServer;
    private String logEventsHtml = "/org/logevents/logevents.html";
    private OpenIdConfiguration openIdConfiguration;
    private LogEventSource logEventSource;
    private Optional<String> cookieEncryptionKey = Optional.empty();
    private CryptoVault sessionVault;
    private Optional<String> keyStore = Optional.empty();
    private Optional<String> keyStorePassword = Optional.empty();
    private Optional<String> hostKeyPassword = Optional.empty();

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setHttpPort(Optional<Integer> httpPort) {
        this.httpPort = httpPort;
    }

    public void setLogEventsHtml(String logEventsHtml) {
        this.logEventsHtml = logEventsHtml;
    }

    public void setOpenIdConfiguration(OpenIdConfiguration openIdConfiguration) {
        this.openIdConfiguration = openIdConfiguration;
    }

    public void setLogEventSource(LogEventSource logEventSource) {
        this.logEventSource = logEventSource;
    }

    public void start() {
        LogEventStatus.getInstance().addInfo(this, "Starting server on port " + httpPort);
        try {
            if (hostname == null) {
                hostname = InetAddress.getLocalHost().getHostName();
            }
            if (httpsPort.isPresent()) {
                HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(hostname, httpsPort.get()), 0);
                try {
                    httpsServer.setHttpsConfigurator(new HttpsConfigurator(createSslContext(hostname)));
                } catch (GeneralSecurityException e) {
                    LogEventStatus.getInstance().addError(this, "Failed to start SSLContext", e);
                    return;
                }
                this.httpServer = httpsServer;
            } else if (httpPort.isPresent()) {
                this.httpServer = HttpServer.create(new InetSocketAddress(hostname, httpPort.get()), 0);
            } else {
                LogEventStatus.getInstance().addError(this, "httpPort or httpsPort must be configured", null);
                return;
            }
            LogEventStatus.getInstance().addInfo(this, "Started on " + getUrl());

            this.httpServer.createContext("/", this::httpHandler);
            this.httpServer.start();
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to start server", e);
        }
        setupCookieVault();
    }

    public String getUrl() {
        String scheme = httpServer instanceof HttpsServer ? "https" : "http";
        return scheme + "://" + hostname + ":" + httpServer.getAddress().getPort() + "/logs";
    }

    void setupCookieVault() {
        this.sessionVault = new CryptoVault(cookieEncryptionKey);
    }

    public SSLContext createSslContext(String hostName) throws GeneralSecurityException, IOException {
        HostKeyStore hostKeyStore = new HostKeyStore(
                new File(keyStore.orElse("key-" + hostName + ".p12")),
                keyStorePassword.orElse("")
        );
        hostKeyStore.setHostName(hostName);
        hostKeyStore.setKeyPassword(hostKeyPassword.orElse(""));
        if (!hostKeyStore.isKeyPresent()) {
            hostKeyStore.generateKey();
        }
        File crtFile = new File("key-" + hostName + ".crt");
        if (!crtFile.exists()) {
            LogEventStatus.getInstance().addInfo(this, "Please import " + crtFile.getAbsolutePath()
                + " as a root CA to access logevents console with your browser over https");
        }
        hostKeyStore.writeCertificate(crtFile);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(hostKeyStore.getKeyManagers(), null, null);
        return sslContext;
    }

    void httpHandler(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/logs")) {
                exchange.getResponseHeaders().add("Location", getAuthority(exchange) + "/logs/");
                exchange.sendResponseHeaders(302, 0);
            } else if (path.equals("/logs/")) {
                String text = getResourceFileAsString(logEventsHtml);
                exchange.getResponseHeaders().add("Content-type", "text/html");
                sendResponse(exchange, text, 200);
            } else if (path.equals("/logs/swagger.json")) {
                Map<String, Object> api = JsonParser.parseObject(getResourceFileAsString("/org/logevents/swagger.json"));
                HashMap<Object, Object> localServer = new HashMap<>();
                localServer.put("url", getAuthority(exchange) + "/logs");
                api.put("servers", Collections.singletonList(localServer));
                exchange.getResponseHeaders().add("Content-type", "application/json");
                sendResponse(exchange, JsonUtil.toIndentedJson(api), 200);
            } else if (path.equals("/logs/login")) {
                String state = OpenIdConfiguration.randomString(50);
                exchange.getResponseHeaders().add("Location", openIdConfiguration.getAuthorizationUrl(
                        state, getAuthority(exchange) + "/logs/oauth2callback"
                ));
                exchange.getResponseHeaders().set("Set-Cookie",
                        "logevents.query=" + exchange.getRequestURI().getRawQuery() + ";Max-Age: 300"
                        +", logevents.login.state=" + state + ";Max-Age; 300");
                exchange.sendResponseHeaders(302, 0);
            } else if (path.equals("/logs/oauth2callback")) {
                Map<String, String[]> parameters = parseParameters(exchange.getRequestURI().getQuery());
                Map<String, Object> idToken = openIdConfiguration.fetchIdToken(
                        parameters.get("code")[0],getAuthority(exchange) + "/logs/oauth2callback"
                );

                logger.warn(AUDIT, "User logged in {}", idToken);
                LogEventStatus.getInstance().addInfo(this, "User logged in " + idToken);

                exchange.getResponseHeaders().set("Set-Cookie", createSessionCookie(idToken));
                exchange.getResponseHeaders().add("Location", getAuthority(exchange) + "/logs");
                exchange.sendResponseHeaders(302, 0);
            } else if (!isAuthenticated(exchange)) {
                sendResponse(exchange, "Please log in", 401);
            } else if (path.equals("/logs/events")) {
                LogEventFilter filter = new LogEventFilter(parseParameters(exchange.getRequestURI().getQuery()));
                LogEventQueryResult queryResult = logEventSource.query(filter);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("facets", queryResult.getSummary().toJson());
                result.put("events", queryResult.getEventsAsJson());

                exchange.getResponseHeaders().add("Content-type", "application/json");
                sendResponse(exchange, JsonUtil.toIndentedJson(result), 200);
            } else {
                sendResponse(exchange, "Unknown file", 404);
            }
        } catch (Exception e) {
            logger.error("While processing {}", exchange, e);
            sendResponse(exchange, e.toString(), 500);
        }
    }

    private String getAuthority(HttpExchange exchange) {
        String scheme = exchange instanceof HttpsExchange ? "https" : "http";
        return scheme + "://" + exchange.getRequestHeaders().getFirst("Host");
    }

    private String createSessionCookie(Map<String, Object> idToken) {
        Map<String, Object> session = new HashMap<>();
        session.put("subject", idToken.get("sub"));
        Instant sessionTime = Instant.ofEpochSecond(Long.parseLong(idToken.get("iat").toString()));
        session.put("sessionTime", sessionTime.toString());
        return "logevents.session=" + sessionVault.encrypt(JsonUtil.toIndentedJson(session));
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        Optional<String> sessionCookie = getCookie(exchange, "logevents.session");
        if (!sessionCookie.isPresent()) {
            return false;
        }
        try {
            Map<String, Object> session = JsonParser.parseObject(sessionVault.decrypt(sessionCookie.get()));
            if (session.containsKey("sessionTime")) {
                Instant sessionTime = Instant.parse(session.get("sessionTime").toString());
                if (Instant.now().isBefore(sessionTime.plusSeconds(60*60))) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogEventStatus.getInstance().addInfo(this, "Failed to decode session cookie");
        }
        exchange.getResponseHeaders().set("Set-Cookie", "logevents.session=; max-age=-1");
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "hostname='" + hostname + '\'' +
                ", httpPort=" + httpPort +
                '}';
    }

    public void setHttpsPort(Optional<Integer> httpsPort) {
        this.httpsPort = httpsPort;
    }

    public CryptoVault getSessionVault() {
        return sessionVault;
    }

    public void setCookieEncryptionKey(Optional<String> cookieEncryptionKey) {
        this.cookieEncryptionKey = cookieEncryptionKey;
    }

    public void setKeyStore(Optional<String> keyStore) {
        this.keyStore = keyStore;
    }

    public void setKeyStorePassword(Optional<String> keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public void setHostKeyPassword(Optional<String> hostKeyPassword) {
        this.hostKeyPassword = hostKeyPassword;
    }
}
