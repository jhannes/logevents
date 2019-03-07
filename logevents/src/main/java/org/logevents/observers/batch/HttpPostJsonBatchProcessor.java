package org.logevents.observers.batch;

import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

public class HttpPostJsonBatchProcessor implements LogEventBatchProcessor {
    private URL url;
    protected JsonLogEventsBatchFormatter jsonMessageFormatter;

    public HttpPostJsonBatchProcessor(URL url, JsonLogEventsBatchFormatter jsonMessageFormatter) {
        this.url = url;
        this.jsonMessageFormatter = jsonMessageFormatter;
    }

    @Override
    public void processBatch(LogEventBatch batch) {
        if (url == null) {
            return;
        }
        Map<String, Object> jsonMessage;
        try {
            jsonMessage = jsonMessageFormatter.createMessage(batch);
        } catch (Exception e) {
            LogEventStatus.getInstance().addFatal(this, "Runtime error generating message", e);
            return;
        }
        try {
            NetUtils.postJson(url, JsonUtil.toIndentedJson(jsonMessage));
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to send slack message", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{jsonMessageFormatter=" + jsonMessageFormatter + ",url=" + url + '}';
    }
}