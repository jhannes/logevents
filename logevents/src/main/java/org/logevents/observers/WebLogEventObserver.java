package org.logevents.observers;

import com.sun.net.httpserver.HttpsServer;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.JsonExceptionFormatter;
import org.logevents.extend.servlets.JsonMessageFormatter;
import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.logevents.util.Configuration;
import org.logevents.util.openid.OpenIdConfiguration;
import org.slf4j.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * You can even include a link to the web dashboard for Logevents in {@link SlackLogEventObserver} by setting the <code>observer.slack.detailUrl</code> configuration parameter to point to your {@link LogEventsServlet}.
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
    private JsonMessageFormatter jsonFormatter = new JsonMessageFormatter();

    private Optional<String> cookieEncryptionKey = Optional.empty();
    private final OpenIdConfiguration openIdConfiguration;
    private JsonExceptionFormatter exceptionFormatter = new JsonExceptionFormatter();
    private String logEventsHtml;

    public WebLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public WebLogEventObserver(Configuration configuration) {
        this(
                new OpenIdConfiguration(configuration),
                configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class)
                );
        configureFilter(configuration);
        this.logEventsHtml = configuration.optionalString("logEventsHtml")
                .orElse("/org/logevents/logevents.html");
        this.exceptionFormatter = configuration.createInstanceWithDefault("exceptionFormatter", JsonExceptionFormatter.class);
        this.cookieEncryptionKey = configuration.optionalString("cookieEncryptionKey");
        this.jsonFormatter = configuration.createInstanceWithDefault("jsonMessageFormatter", JsonMessageFormatter.class);
        configuration.checkForUnknownFields();
    }

    public WebLogEventObserver(OpenIdConfiguration openIdConfiguration, MessageFormatter messageFormatter) {
        this.openIdConfiguration = openIdConfiguration;
        this.messageFormatter = messageFormatter;
    }

    public WebLogEventObserver() {
        messageFormatter = new MessageFormatter();
        openIdConfiguration = null;
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

    public Map<String, Object> format(LogEvent event) {
        Map<String, Object> jsonEvent = new HashMap<>();

        jsonEvent.put("thread", event.getThreadName());
        jsonEvent.put("time", event.getInstant().toString());
        jsonEvent.put("logger", event.getLoggerName());
        jsonEvent.put("abbreviatedLogger", event.getAbbreviatedLoggerName(0));
        jsonEvent.put("level", event.getLevel().name());
        jsonEvent.put("levelIcon", JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()));
        jsonEvent.put("formattedMessage", messageFormatter.format(event.getMessage(), event.getArgumentArray()));
        jsonEvent.put("messageTemplate", event.getMessage());
        jsonEvent.put("message", jsonFormatter.format(event.getMessage(), event.getArgumentArray()));
        jsonEvent.put("marker", Optional.ofNullable(event.getMarker()).map(Marker::getName).orElse(null));

        if (event.getThrowable() != null) {
            jsonEvent.put("throwable", event.getThrowable().toString());
            jsonEvent.put("stackTrace", exceptionFormatter.createStackTrace(event.getThrowable()));
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
}
