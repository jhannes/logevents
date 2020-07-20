package org.logevents.observers;

import org.logevents.config.Configuration;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;

/**
 * Convenience superclass for observers that send JSON over HTTP
 */
public abstract class AbstractHttpPostJsonLogEventObserver extends AbstractBatchingLogEventObserver {
    private final URL url;
    private Proxy proxy = Proxy.NO_PROXY;

    public AbstractHttpPostJsonLogEventObserver(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void configureProxy(Configuration configuration) {
        configuration.optionalString("proxy").ifPresent(proxyHost -> {
            int colonPos = proxyHost.lastIndexOf(':');
            String hostname = colonPos != -1 ? proxyHost.substring(0, colonPos) : proxyHost;
            int proxyPort = colonPos != -1 ? Integer.parseInt(proxyHost.substring(colonPos+1)) : 80;
            this.proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(hostname, proxyPort));
        });
    }

    @Override
    public void processBatch(LogEventBatch batch) {
        sendBatch(batch, this::formatBatch);
    }

    protected void sendBatch(LogEventBatch batch, Function<LogEventBatch, Map<String, Object>> formatter) {
        if (batch.isEmpty()) {
            return;
        }
        if (url == null) {
            LogEventStatus.getInstance().addInfo(this, "No url - batch discarded");
            return;
        }
        Map<String, Object> jsonMessage;
        try {
            jsonMessage = formatter.apply(batch);
        } catch (Exception e) {
            LogEventStatus.getInstance().addFatal(this, "Runtime error generating message", e);
            return;
        }
        try {
            String response = postJson(jsonMessage);
            LogEventStatus.getInstance().addTrace(this, "Sent message to " + url + ": " + response);
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to send message to " + url, e);
        }
    }

    /**
     * Override this method to customize how to execute the HTTP Post request.
     * For example, this is a good place to put authentication logic
     */
    protected String postJson(Map<String, Object> jsonMessage) throws IOException {
        return NetUtils.postJson(url, JsonUtil.toIndentedJson(jsonMessage), proxy);
    }

    /**
     * Override this method to customize how the {@link LogEventBatch} will be
     * formatted as JSON.
     */
    protected abstract Map<String, Object> formatBatch(LogEventBatch batch);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{url=" + url + '}';
    }
}
