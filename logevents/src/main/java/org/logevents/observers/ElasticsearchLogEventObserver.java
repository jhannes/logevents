package org.logevents.observers;

import static org.logevents.util.NetUtils.NO_AUTHORIZATION_HEADER;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.JsonLogEventFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.status.LogEventStatus;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Publishes asynchronously to Elasticsearch with the Elasticsearch
 * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/indices.html">Index API</a>
 *
 * <h3>Sample configuration</h3>
 * <pre>
 * observer.elastic=ElasticSearchLogEventObserver
 * observer.elastic.elasticsearchUrl=http://localhost:9200
 * observer.elastic.elasticsearchUrlPath=_bulk
 * observer.elastic.elasticsearchAuthorizationHeader=repositoryId injectApiToken
 * observer.elastic.index=my-test-index
 * observer.elastic.idleThreshold=PT2S
 * observer.elastic.cooldownTime=PT1S
 * observer.elastic.maximumWaitTime=PT30S
 * observer.elastic.suppressMarkers=PERSONAL_DATA
 * observer.elastic.formatter.excludedMdcKeys=secret
 * </pre>
 *
 * <h3>Elasticsearch configuration</h3>
 *
 * <ul>
 *     <li><code>elasticsearchUrl</code> should point to where the elasticsearch API lives. It should contain an URI scheme and authority.</li>
 *     <li><code>elasticsearchUrlPath</code> should point to the http path where the Elasticsearch Bulk API lives.</li>
 * </ul>
 *
 * <h4>Authorization</h4>
 *
 * The configurable <code>elasitcsearchAuthorizationHeader</code> is the value the client will include as
 * Authorization header
 * when communicating with <code>elasticsearchUrl</code>. It is not to be confused by Basic authentication. If you
 * need basic authentication you need to remember to provide its configuration value as '<code>Basic
 * base64encodedValueHere</code>'.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc3986#section-3">RFC3986 #section3 about URI syntax</a>
 */
public class ElasticsearchLogEventObserver extends AbstractBatchingLogEventObserver {

    private static final String DEFAULT_ELASTICSEARCH_BULK_API_PATH = "_bulk";
    private static final String APPLICATION_X_NDJSON = "application/x-ndjson";
    private final URL elasticsearchUrl;
    private final String elasticsearchUrlPath;
    private final String elasticsearchAuthorizationHeaderValue;
    private final String index;
    private final JsonLogEventFormatter formatter;

    private Proxy proxy = Proxy.NO_PROXY;

    public ElasticsearchLogEventObserver(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public ElasticsearchLogEventObserver(Configuration configuration) {
        this.elasticsearchUrl = configuration.getUrl("elasticsearchUrl");
        this.elasticsearchUrlPath = configuration.optionalString("elasticsearchUrlPath").orElse(getDefaultPath());
        this.elasticsearchAuthorizationHeaderValue = configuration.optionalString("elasticsearchAuthorizationHeader").orElse(NO_AUTHORIZATION_HEADER);
        this.index = configuration.getString("index");
        this.formatter = configuration.createInstanceWithDefault("formatter", JsonLogEventFormatter.class);
        this.configureBatching(configuration);
        this.configureFilter(configuration, Level.TRACE);
        this.configureMarkers(configuration);
        this.configureProxy(configuration);
        configuration.checkForUnknownFields();
    }

    protected String getDefaultPath() {
        return DEFAULT_ELASTICSEARCH_BULK_API_PATH;
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

        URL url = new URL(elasticsearchUrl, elasticsearchUrlPath);
        HttpURLConnection connection = NetUtils.post(
            url,
            String.join("\n", jsons),
            APPLICATION_X_NDJSON,
            proxy,
            elasticsearchAuthorizationHeaderValue);

        return parseBulkApiResponse(JsonParser.parseObject(connection));
    }

    protected List<String> parseBulkApiResponse(Map<String, Object> response) throws IOException {
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
