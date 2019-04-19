package org.logevents.observers;

import com.sun.net.httpserver.HttpsServer;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.formatting.MessageFormatter;
import org.logevents.util.Configuration;
import org.logevents.util.openid.OpenIdConfiguration;

import java.util.Optional;
import java.util.Properties;


/**
 * Used to collect messages in an {@link LogEventBuffer} for use by
 * {@link LogEventsServlet}. In order to
 * set up, you need to:
 *
 * <ol>
 *    <li>Register an OpenID Connect provider to authenticate the users of the servlet (see {@link OpenIdConfiguration})</li>
 *    <li>Set up a WebLogEventObserver named <code>observer.servlet</code>, and</li>
 *    <li>Add a {@link LogEventsServlet} to your servlet container</li>
 * </ol>
 *
 * You can even include a link to the web dashboard for Logevents in {@link SlackLogEventObserver} by setting the <code></code>
 *
 * <h2>Sample configuration</h2>
 *
 * <pre>
 * observer.servlet=WebLogEventObserver
 * observer.servlet.openIdIssuer=https://login.microsoftonline.com/common
 * observer.servlet.clientId=12345678-abcd-pqrs-9876-9abcdef01234
 * observer.servlet.clientSecret=3¤..¤!?qwer
 * </pre>
 *
 * Possible future improvements: a. Run with {@link HttpsServer},
 * b. Use a database backend, c. post log messages over https to a log concentrator server
 *
 * @see LogEventsServlet
 * @see OpenIdConfiguration
 */
public class WebLogEventObserver extends FilteredLogEventObserver {

    /**
     * In order to survive reload of configuration, it's useful to have a static message buffer
     */
    private static final LogEventBuffer logEventBuffer = new LogEventBuffer();
    private final MessageFormatter messageFormatter;

    private Optional<String> cookieEncryptionKey = Optional.empty();
    private final OpenIdConfiguration openIdConfiguration;
    private String[] packageFilter;

    public WebLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public WebLogEventObserver(Configuration configuration) {
        this(
                new OpenIdConfiguration(configuration),
                configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class)
                );
        configureFilter(configuration);
        setPackageFilter(configuration.getStringList("packageFilter"));
        //formatter.configureSourceCode(configuration);
        this.cookieEncryptionKey = configuration.optionalString("cookieEncryptionKey");
        configuration.checkForUnknownFields();
    }

    public WebLogEventObserver(OpenIdConfiguration openIdConfiguration, MessageFormatter messageFormatter) {
        this.openIdConfiguration = openIdConfiguration;
        this.messageFormatter = messageFormatter;
    }

    public Optional<String> getCookieEncryptionKey() {
        return cookieEncryptionKey;
    }

    @Override
    protected void doLogEvent(LogEvent logEvent) {
        logEventBuffer.logEvent(logEvent);
    }

    public OpenIdConfiguration getOpenIdConfiguration() {
        return openIdConfiguration;
    }

    public LogEventBuffer getLogEventBuffer() {
        return logEventBuffer;
    }

    protected boolean isIgnored(StackTraceElement frame) {
        for (String filter : this.packageFilter) {
            if (frame.getClassName().startsWith(filter)) {
                return true;
            }
        }
        return false;
    }

    public void setPackageFilter(String[] packageFilter) {
        this.packageFilter = packageFilter;
    }

    public MessageFormatter getMessageFormatter() {
        return messageFormatter;
    }
}
