package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.formatting.MessageFormatter;
import org.logevents.util.openid.OpenIdConfiguration;
import org.logevents.web.CryptoVault;
import org.logevents.web.LogEventHttpServer;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Optional;
import java.util.Properties;


/**
 * Used to collect messages in an {@link LogEventBuffer} for use by {@link LogEventsServlet}
 * or {@link LogEventHttpServer}.
 *
 * <h2>Setup with servlet container</h2>
 * In order to set up, you need to:
 *
 * <ol>
 *    <li>Register an OpenID Connect provider to authenticate the users of the servlet (see {@link OpenIdConfiguration})</li>
 *    <li>Set up a WebLogEventObserver named <code>observer.servlet</code>, and</li>
 *    <li>Add a {@link LogEventsServlet} to your servlet container</li>
 * </ol>
 *
 * <h2>Setup with standalone server</h2>
 *
 * If you have direct http or https-access to your application, WebLogEventObserver can start an embedded
 * web server at a port you specify with <code>observer.servlet.httpPort</code> or
 * <code>observer.servlet.httpsPort</code>. See {@link LogEventHttpServer} for more info.
 *
 * You can even include a link to the web dashboard for Logevents in {@link SlackLogEventObserver} by setting the <code>observer.slack.detailUrl</code> configuration parameter to point to your {@link LogEventsServlet}.
 *
 * <h2>Sample configuration</h2>
 *
 * <pre>
 * observer.servlet=WebLogEventObserver
 * observer.servlet.openIdIssuer=https://login.microsoftonline.com/common
 * observer.servlet.clientId=12345678-abcd-pqrs-9876-9abcdef01234
 * observer.servlet.clientSecret=3¤..¤!?qwer
 * observer.servlet.cookieEncryptionKey=32s...r2
 * observer.servlet.source=DatabaseLogEventObserver
 * observer.servlet.source.jdbcUrl=...
 * observer.servlet.httpPort=8080
 * </pre>
 *
 * @see LogEventsServlet
 * @see OpenIdConfiguration
 */
public class WebLogEventObserver extends FilteredLogEventObserver {

    private final MessageFormatter messageFormatter;
    private final LogEventSource source;
    private CryptoVault cookieVault;
    private LogEventHttpServer logEventServer;

    private final OpenIdConfiguration openIdConfiguration;
    private String logEventsHtml = "/org/logevents/logevents.html";

    public WebLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public WebLogEventObserver(Configuration configuration) {
        this(
                new OpenIdConfiguration(configuration),
                configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class),
                configuration.createInstanceWithDefault("source", LogEventSource.class, LogEventBuffer.class)
        );
        configureFilter(configuration);
        this.logEventsHtml = configuration.optionalString("logEventsHtml")
                .orElse("/org/logevents/logevents.html");
        cookieVault = new CryptoVault(configuration.optionalString("cookieEncryptionKey")
                .orElseGet(() -> CryptoVault.randomString(40)));
        Optional<Integer> httpPort = configuration.optionalInt("httpPort");
        Optional<Integer> httpsPort = configuration.optionalInt("httpsPort");
        if (httpPort.isPresent() || httpsPort.isPresent()) {
            logEventServer = createHttpServer(configuration, httpPort, httpsPort);
            logEventServer.start();
        }
        configuration.checkForUnknownFields();
    }

    public WebLogEventObserver(OpenIdConfiguration openIdConfiguration, MessageFormatter messageFormatter, LogEventSource source) {
        this.openIdConfiguration = openIdConfiguration;
        this.messageFormatter = messageFormatter;
        this.source = source;
        cookieVault = new CryptoVault(CryptoVault.randomString(40));
    }

    public WebLogEventObserver() {
        messageFormatter = new MessageFormatter();
        openIdConfiguration = null;
        source = new LogEventBuffer();
        cookieVault = new CryptoVault(CryptoVault.randomString(40));
    }

    protected LogEventHttpServer createHttpServer(Configuration configuration, Optional<Integer> httpPort, Optional<Integer> httpsPort) {
        LogEventHttpServer logEventServer = new LogEventHttpServer();
        logEventServer.setHostname(configuration.optionalString("hostname").orElse(null));
        logEventServer.setHttpPort(httpPort);
        logEventServer.setHttpsPort(httpsPort);
        logEventServer.setLogEventsHtml(logEventsHtml);
        logEventServer.setOpenIdConfiguration(openIdConfiguration);
        logEventServer.setLogEventSource(source);
        logEventServer.setCookieVault(cookieVault);
        logEventServer.setKeyStore(configuration.optionalString("keyStore"));
        logEventServer.setKeyStorePassword(configuration.optionalString("keyStorePassword"));
        logEventServer.setHostKeyPassword(configuration.optionalString("hostKeyPassword"));
        return logEventServer;
    }

    public CryptoVault getCookieVault() {
        return cookieVault;
    }

    @Override
    protected void doLogEvent(LogEvent logEvent) {
        source.logEvent(logEvent);
    }

    public OpenIdConfiguration getOpenIdConfiguration() {
        return openIdConfiguration;
    }

    public LogEventSource getLogEventSource() {
        return source;
    }

    public MessageFormatter getMessageFormatter() {
        return messageFormatter;
    }

    public String getLogEventsHtml() {
        return logEventsHtml;
    }

    public void setLogEventsHtml(String logEventsHtml) {
        this.logEventsHtml = logEventsHtml;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "openIdConfiguration=" + openIdConfiguration +
                '}';
    }

    public String getServerUrl() {
        return logEventServer.getUrl();
    }

    public X509Certificate getCertificate() {
        return logEventServer.getCertificate();
    }

    protected String createSessionCookie(String subject) {
        HashMap<String, Object> idToken = new HashMap<>();
        idToken.put("sub", subject);
        idToken.put("iat", System.currentTimeMillis()/1000);
        return logEventServer.createSessionCookie(idToken);
    }
}
