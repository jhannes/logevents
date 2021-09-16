package org.logeventsdemo.servlets;

import org.logevents.extend.servlets.LogEventsServlet;
import org.logevents.observers.LogEventSource;
import org.logevents.util.openid.OpenIdConfiguration;
import org.logevents.observers.web.CryptoVault;

import java.util.Optional;

class LogViewerServlet extends LogEventsServlet {

    private Optional<OpenIdConfiguration> openIdConfiguration = Optional.empty();
    private Optional<LogEventSource> logEventSource = Optional.empty();
    private CryptoVault cookieVault = new CryptoVault(CryptoVault.randomString(50));

    @Override
    protected OpenIdConfiguration getOpenIdConfiguration() {
        return openIdConfiguration.orElseThrow(() -> new IllegalStateException("Missing openIdConfiguration"));
    }

    @Override
    protected LogEventSource getLogEventSource() {
        return logEventSource.orElseThrow(() -> new IllegalStateException("Missing logEventSource"));
    }

    @Override
    protected String getLogEventsHtml() {
        return "/org/logevents/logevents.html";
    }

    @Override
    protected synchronized CryptoVault getCookieVault() {
        return cookieVault;
    }

    public void setOpenIdConfiguration(OpenIdConfiguration openIdConfiguration) {
        this.openIdConfiguration = Optional.of(openIdConfiguration);
    }

    public void setLogEventSource(LogEventSource logEventSource) {
        this.logEventSource = Optional.of(logEventSource);
    }

    public void setCookieEncryptionKey(String cookiePassword) {
        this.cookieVault = new CryptoVault(Optional.ofNullable(cookiePassword).orElseGet(() -> CryptoVault.randomString(40)));
    }
}
