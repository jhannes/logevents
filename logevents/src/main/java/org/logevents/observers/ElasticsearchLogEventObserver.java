package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.ExceptionFormatter;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class ElasticsearchLogEventObserver extends BatchingLogEventObserver {

    private final URL elasticsearchUrl;
    private final String index;
    private MessageFormatter messageFormatter = new MessageFormatter();

    private ExceptionFormatter exceptionFormatter = new ExceptionFormatter();

    public ElasticsearchLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public ElasticsearchLogEventObserver(Configuration configuration) {
        this(configuration.getUrl("elasticsearchUrl"), configuration.getString("index"));
        this.configureBatching(configuration);
        this.configureFilter(configuration);
        this.messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class);
        configuration.checkForUnknownFields();
    }

    public ElasticsearchLogEventObserver(URL elasticsearchUrl, String index) {
        this.elasticsearchUrl = elasticsearchUrl;
        this.index = index;
    }

    @Override
    protected void processBatch(LogEventBatch batch) {
        try {
            indexDocuments(batch);
            LogEventStatus.getInstance().addDebug(this, "Flushed " + batch.size() + " messages to " + getUrl());
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to send message to " + getUrl(), e);
        }
    }

    public Map<String, Object> formatMessage(LogEvent event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("@timestamp", event.getInstant().toString());
        doc.put("thread", event.getThreadName());
        doc.put("level", event.getLevel().toString());
        doc.put("logger", event.getLoggerName());
        doc.put("message", event.getMessage());
        doc.put("formattedMessage", messageFormatter.format(event.getMessage(), event.getArgumentArray()));
        doc.put("marker", event.getMarker() != null ? event.getMarker().getName() : null);
        if (event.getThrowable() != null) {
            doc.put("exception.class", event.getThrowable().getClass().getName());
            doc.put("exception.message", event.getThrowable().getMessage());
            doc.put("exception.stacktrace", exceptionFormatter.format(event.getThrowable()));
        }
        event.getMdcProperties().forEach((k, v) -> doc.put("mdc." + k, v));
        return doc;
    }

    public Map<String, Object> getIndexHeader() {
        Map<String, Object> header = new HashMap<>();
        HashMap<Object, Object> headerIndex = new HashMap<>();
        headerIndex.put("_index", index);
        header.put("index", headerIndex);
        return header;
    }

    public String getIndex() {
        return index;
    }

    List<String> indexDocuments(Iterable<LogEvent> logEvents) throws IOException {
        List<String> jsons = new ArrayList<>();
        for (LogEvent logEvent : logEvents) {
            jsons.add(new JsonUtil("", "").toJson(getIndexHeader()));
            jsons.add(new JsonUtil("", "").toJson(formatMessage(logEvent)));
        }
        jsons.add("");

        URL url = new URL(this.elasticsearchUrl, "_bulk");
        HttpURLConnection connection = NetUtils.post(url,
                String.join("\n", jsons), "application/x-ndjson");

        Map<String, Object> response = JsonParser.parseObject(connection);

        List<Map<String, Object>> items = JsonUtil.getObjectList(response, "items");
        return items.stream()
                .map(o -> JsonUtil.getObject(o, "index"))
                .map(o -> o.get("_index") + "/_doc/" + o.get("_id"))
                .collect(Collectors.toList());
    }

    public URL getUrl() {
        return this.elasticsearchUrl;
    }
}
