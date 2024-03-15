package org.logevents.observers.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsServer;
import org.logevents.observers.LogEventSource;
import org.logevents.observers.WebLogEventObserver;
import org.logevents.query.LogEventQuery;
import org.logevents.query.LogEventQueryResult;
import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.openid.OpenIdConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// TODO state
/**
 * The simplest way to expose your logs in your web browser, LogEventHttpServer runs an embedded http server
 * in your application for the logs app console. Requires that you configure an {@link OpenIdConfiguration}
 * so you don't accidentally expose it insecurely. See {@link OpenIdConfiguration} for details on how to
 * set this up.
 *
 * <h2>Running with http (unencrypted)</h2>
 *
 * The simplest way to get {@link LogEventHttpServer} up a running is to use <code>http</code>. However,
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
 * This allows you to access you log from localhost. If you want remote access, you should put your
 * application behind a https reverse proxy.
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
    private HttpServer httpServer;
    private String logEventsHtml = "/org/logevents/logevents.html";
    private OpenIdConfiguration openIdConfiguration;
    private LogEventSource logEventSource;
    private CryptoVault cookieVault;

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
        LogEventStatus.getInstance().addConfig(this, "Starting server on port " + httpPort);
        try {
            if (hostname == null) {
                hostname = InetAddress.getLocalHost().getHostName();
            }
            if (httpPort.isPresent()) {
                this.httpServer = HttpServer.create(new InetSocketAddress(hostname, httpPort.get()), 0);
            } else {
                LogEventStatus.getInstance().addError(this, "httpPort or httpsPort must be configured", null);
                return;
            }
            LogEventStatus.getInstance().addConfig(this, "Started on " + getUrl());

            this.httpServer.createContext("/", this::httpHandler);
            this.httpServer.start();
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to start server", e);
        }
    }

    public String getUrl() {
        String scheme = httpServer instanceof HttpsServer ? "https" : "http";
        return scheme + "://" + hostname + ":" + httpServer.getAddress().getPort() + "/logs";
    }

    protected void httpHandler(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/logs")) {
                exchange.getResponseHeaders().add("Location", getAuthority(exchange) + "/logs/");
                exchange.sendResponseHeaders(302, 0);
            } else if (path.equals("/logs/")) {
                serveResource(exchange, logEventsHtml, "text/html");
            } else if (path.matches("/logs/[a-zA-Z._-]+\\.css")) {
                serveResource(exchange, "/org/logevents" + path.substring("/logs".length()), "text/css");
            } else if (path.matches("/logs/[a-zA-Z._-]+\\.js")) {
                serveResource(exchange, "/org/logevents" + path.substring("/logs".length()), "text/javascript");
            } else if (path.equals("/logs/openapi.json")) {
                Map<String, Object> api = JsonParser.parseObject(getResourceFileAsString("/org/logevents/openapi.json"));
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
                if (!openIdConfiguration.isAuthorizedToken(idToken)) {
                    logger.warn(AUDIT, "Unknown user tried to log in {}", idToken);
                    exchange.sendResponseHeaders(403, 0);
                    return;
                }

                logger.warn(AUDIT, "User logged in {}", idToken);
                LogEventStatus.getInstance().addConfig(this, "User logged in " + idToken);

                exchange.getResponseHeaders().set("Set-Cookie", createSessionCookie(idToken));
                exchange.getResponseHeaders().add("Location", getAuthority(exchange) + "/logs");
                exchange.sendResponseHeaders(302, 0);
            } else if (!isAuthenticated(exchange)) {
                sendResponse(exchange, "Please log in", 401);
            } else if (path.equals("/logs/events")) {
                LogEventQuery query = new LogEventQuery(parseParameters(exchange.getRequestURI().getQuery()));
                LogEventQueryResult queryResult = logEventSource.query(query);

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

    private void serveResource(HttpExchange exchange, String logEventsHtml, String s) throws IOException {
        String text = getResourceFileAsString(logEventsHtml);
        if (text != null) {
            exchange.getResponseHeaders().add("Content-type", s);
            sendResponse(exchange, text, 200);
        } else {
            sendResponse(exchange, "File not found", 404);
        }
    }

    private String getAuthority(HttpExchange exchange) {
        String scheme = exchange instanceof HttpsExchange ? "https" : "http";
        return scheme + "://" + exchange.getRequestHeaders().getFirst("Host");
    }

    public String createSessionCookie(Map<String, Object> idToken) {
        Map<String, Object> session = new HashMap<>();
        session.put("subject", idToken.get("sub"));
        Instant sessionTime = Instant.ofEpochSecond(Long.parseLong(idToken.get("iat").toString()));
        session.put("sessionTime", sessionTime.toString());
        return "logevents.session=" + cookieVault.encrypt(JsonUtil.toIndentedJson(session));
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        Optional<String> sessionCookie = getCookie(exchange, "logevents.session");
        if (!sessionCookie.isPresent()) {
            return false;
        }
        try {
            Map<String, Object> session = JsonParser.parseObject(cookieVault.decrypt(sessionCookie.get()));
            if (session.containsKey("sessionTime")) {
                Instant sessionTime = Instant.parse(session.get("sessionTime").toString());
                if (Instant.now().isBefore(sessionTime.plusSeconds(60*60))) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogEventStatus.getInstance().addConfig(this, "Failed to decode session cookie");
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

    public CryptoVault getCookieVault() {
        return cookieVault;
    }

    public void setCookieVault(CryptoVault cookieVault) {
        this.cookieVault = cookieVault;
    }

}
