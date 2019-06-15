package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.JsonExceptionFormatter;
import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.formatting.MessageFormatter;
import org.logevents.util.openid.OpenIdConfiguration;
import org.logevents.web.LogEventHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private LogEventHttpServer logEventServer;

    private Optional<String> cookieEncryptionKey = Optional.empty();
    private final OpenIdConfiguration openIdConfiguration;
    private JsonExceptionFormatter exceptionFormatter = new JsonExceptionFormatter();
    private String logEventsHtml;
    private static final Logger logger = LoggerFactory.getLogger(WebLogEventObserver.class);

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
        this.exceptionFormatter = configuration.createInstanceWithDefault("exceptionFormatter", JsonExceptionFormatter.class);
        this.cookieEncryptionKey = configuration.optionalString("cookieEncryptionKey");
        Optional<Integer> httpPort = configuration.optionalInt("httpPort");
        Optional<Integer> httpsPort = configuration.optionalInt("httpsPort");
        if (httpPort.isPresent() || httpsPort.isPresent()) {
            this.logEventServer = new LogEventHttpServer();
            logEventServer.setHostname(configuration.optionalString("hostname").orElse(null));
            logEventServer.setHttpPort(httpPort);
            logEventServer.setHttpsPort(httpsPort);
            logEventServer.setLogEventsHtml(logEventsHtml);
            logEventServer.setOpenIdConfiguration(openIdConfiguration);
            logEventServer.setLogEventSource(source);
            logEventServer.setCookieEncryptionKey(cookieEncryptionKey);
            logEventServer.setKeyStore( configuration.optionalString("keyStore"));
            logEventServer.setKeyStorePassword(configuration.optionalString("keyStorePassword"));
            logEventServer.setHostKeyPassword(configuration.optionalString("hostKeyPassword"));
            logEventServer.start();
        }
        configuration.checkForUnknownFields();
    }

    public WebLogEventObserver(OpenIdConfiguration openIdConfiguration, MessageFormatter messageFormatter, LogEventSource source) {
        this.openIdConfiguration = openIdConfiguration;
        this.messageFormatter = messageFormatter;
        this.source = source;
    }

    public WebLogEventObserver() {
        messageFormatter = new MessageFormatter();
        openIdConfiguration = null;
        source = new LogEventBuffer();
    }

    public Optional<String> getCookieEncryptionKey() {
        return cookieEncryptionKey;
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

    public JsonExceptionFormatter getExceptionFormatter() {
        return exceptionFormatter;
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
}
