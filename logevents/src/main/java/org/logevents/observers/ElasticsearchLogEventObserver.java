package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.JsonLogEventFormatter;
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

/**
 * Publishes asynchronously to Elasticsearch with the Elasticsearch
 * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/indices.html">Index API</a>
 *
 * <h3>Sample configuration</h3>
 * <pre>
 * observer.elastic=ElasticSearchLogEventObserver
 * observer.elastic.elasticsearchUrl=http://localhost:9200
 * observer.elastic.index=my-test-index
 * observer.elastic.idleThreshold=PT2S
 * observer.elastic.cooldownTime=PT1S
 * observer.elastic.maximumWaitTime=PT30S
 * observer.elastic.suppressMarkers=PERSONAL_DATA
 * observer.elastic.formatter.excludedMdcKeys=secret
 * </pre>
 */
public class ElasticsearchLogEventObserver extends AbstractBatchingLogEventObserver {

    private final URL elasticsearchUrl;
    private final String index;
    private final JsonLogEventFormatter formatter;

    public ElasticsearchLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public ElasticsearchLogEventObserver(Configuration configuration) {
        this.elasticsearchUrl = configuration.getUrl("elasticsearchUrl");
        this.index = configuration.getString("index");
        this.formatter = configuration.createInstanceWithDefault("formatter", JsonLogEventFormatter.class);
        this.configureBatching(configuration);
        this.configureFilter(configuration);
        configuration.checkForUnknownFields();
    }

    public ElasticsearchLogEventObserver(URL elasticsearchUrl, String index) {
        this.elasticsearchUrl = elasticsearchUrl;
        this.index = index;
        this.formatter = new JsonLogEventFormatter();
    }

    @Override
    protected void processBatch(LogEventBatch batch) {
        try {
            LogEventStatus.getInstance().addTrace(this, "Flushing " + batch.size() + " messages to " + getUrl());
            indexDocuments(batch);
            LogEventStatus.getInstance().addDebug(this, "Flushed " + batch.size() + " messages to " + getUrl());
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to send message to " + getUrl(), e);
        }
    }

    public Map<String, Object> formatMessage(LogEvent event) {
        return formatter.toJsonObject(event);
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
