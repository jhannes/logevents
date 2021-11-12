package org.logevents.observers;

import static org.logevents.util.NetUtils.NO_AUTHORIZATION_HEADER;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.JsonLogEventFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.status.LogEventStatus;
import org.logevents.util.ContentType;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;

/**
 * Publishes asynchronously to Humio
 * <a href="https://library.humio.com/stable/docs/ingesting-data/log-shippers/other-log-shippers/#elasticsearch-bulk-api">Elasticsearch Bulk API</a>
 *
 * <h3>Sample configuration</h3>
 * <pre>
 * observer.humio=HumioLogEventObserver
 * observer.humio.elasticsearchUrl=http://localhost:9200
 * observer.humio.elasticsearchUrlPath=api/v1/ingest/elastic-bulk
 * observer.humio.elasticsearchAuthorizationHeader=repositoryId injectApiToken
 * observer.humio.index=my-test-index
 * observer.humio.idleThreshold=PT2S
 * observer.humio.cooldownTime=PT1S
 * observer.humio.maximumWaitTime=PT30S
 * observer.humio.suppressMarkers=PERSONAL_DATA
 * observer.humio.formatter.excludedMdcKeys=secret
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
 * base64encodeValueHere</code>'.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc3986#section-3">RFC3986 #section3 about URI syntax</a>
 */
public class HumioLogEventObserver extends ElasticsearchLogEventObserver {

    public static final String DEFAULT_HUMIO_BULK_API_PATH = "api/v1/ingest/elastic-bulk";
    public static final List<String> HUMIO_API_HAS_NO_SANE_BULK_API_RESPONSE_CONTAINING_DOCUMENT_IDS_FROM_INSERT =
        Collections.emptyList();

    public HumioLogEventObserver(Map<String, String> properties, String prefix) {
        super(properties, prefix);
    }

    public HumioLogEventObserver(Configuration configuration) {
        super(configuration);
    }

    public HumioLogEventObserver(URL elasticsearchUrl, String index) {
        super(elasticsearchUrl, index);
    }

    public HumioLogEventObserver(URL elasticsearchUrl, String elasticsearchAuthorizationHeader, String index) {
        super(elasticsearchUrl, elasticsearchAuthorizationHeader, index);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_HUMIO_BULK_API_PATH;
    }

    @Override
    protected List<String> parseBulkApiResponse(Map<String, Object> response) throws IOException {
        boolean isAnyApiErrors = Boolean.parseBoolean(String.valueOf(JsonUtil.getField(response, "errors")));
        if (isAnyApiErrors) {
            List<Map<String, Object>> items = JsonUtil.getObjectList(response, "items");
            long numberOfFailedMessages = items.stream()
                .filter(o -> !String.valueOf(JsonUtil.getField(JsonUtil.getObject(o, "create"), "status")).equals("200"))
                .count();

            throw new IOException("Failed sending "+ numberOfFailedMessages + " out of " + items.size()  + " entries");
        }
        return HUMIO_API_HAS_NO_SANE_BULK_API_RESPONSE_CONTAINING_DOCUMENT_IDS_FROM_INSERT;
    }
}
