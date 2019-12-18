package org.logevents.observers;

import org.logevents.observers.batch.LogEventBatch;
import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;

/**
 * Convenience superclass for observers that send JSON over HTTP
 */
public abstract class HttpPostJsonLogEventObserver extends BatchingLogEventObserver {
    private URL url;

    public HttpPostJsonLogEventObserver(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
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
        return NetUtils.postJson(url, JsonUtil.toIndentedJson(jsonMessage));
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
