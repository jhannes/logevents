package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.util.Configuration;
import org.logevents.util.openid.OpenIdConfiguration;

import java.util.Optional;
import java.util.Properties;

public class WebLogEventObserver extends FilteredLogEventObserver {

    /**
     * In order to survive reload of configuration, it's useful to have a static message buffer
     */
    private static final LogEventBuffer logEventBuffer = new LogEventBuffer();

    private Optional<String> cookieEncryptionKey = Optional.empty();
    private final OpenIdConfiguration openIdConfiguration;

    public WebLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public WebLogEventObserver(Configuration configuration) {
        this(new OpenIdConfiguration(configuration));
        configureFilter(configuration);
        this.cookieEncryptionKey = configuration.optionalString("cookieEncryptionKey");
        configuration.checkForUnknownFields();
    }

    public WebLogEventObserver(OpenIdConfiguration openIdConfiguration) {
        this.openIdConfiguration = openIdConfiguration;
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
}
