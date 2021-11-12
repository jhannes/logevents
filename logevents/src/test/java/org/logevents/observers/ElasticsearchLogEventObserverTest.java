package org.logevents.observers;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.logevents.util.ExceptionUtil;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ElasticsearchLogEventObserverTest {

    private ElasticsearchLogEventObserver observer = new ElasticsearchLogEventObserver(toURL("http://localhost:9200"), "logevents-unit-test");

    private URL toURL(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    @Test
    public void shouldFormatSimpleLogEvent() {
        LogEvent event = new LogEventSampler().withMarker().build();
        Map<String, Object> payload = observer.formatMessage(event);

        assertEquals(event.getInstant(), ZonedDateTime.parse(payload.get("@timestamp").toString()).toInstant());
        assertEquals(event.getLoggerName(), payload.get("logger"));
        assertEquals(event.getLevel().name(), payload.get("level"));
        assertEquals(event.getMessage(), payload.get("message"));
        assertEquals(event.getThreadName(), payload.get("thread"));
        assertEquals(event.getMarker().getName(), payload.get("marker"));
    }

    @Test
    public void shouldWriteFormattedMessage() {
        LogEvent event = new LogEventSampler().withArgs("a", "b", "c").build();
        Map<String, Object> payload = observer.formatMessage(event);

        assertEquals(event.getMessage(new MessageFormatter()), payload.get("message"));
    }

    @Test
    public void shouldWriteMdcValues() {
        LogEvent event = new LogEventSampler().withMdc("ip", "10.0.12.11").withMdc("op", "execute").build();
        Map<String, Object> payload = observer.formatMessage(event);

        assertEquals(event.getMdcProperties().get("ip"), JsonUtil.getObject(payload, "mdc").get("ip"));
        assertEquals(event.getMdcProperties().get("op"), JsonUtil.getObject(payload, "mdc").get("op"));
    }

    @Test
    public void shouldWriteExceptions() {
        LogEvent event = new LogEventSampler().withThrowable().build();
        Map<String, Object> payload = observer.formatMessage(event);

        assertEquals(payload.get("exception.class"), event.getThrowable().getClass().getName());
        assertEquals(payload.get("exception.message"), event.getThrowable().getMessage());
        assertContains("at org.logeventsdemo.internal.MyClassName.internalMethod(MyClassName.java:311)",
            payload.get("exception.stackTrace").toString());
    }

    @Test
    public void getBatchHeader() {
        Map<String, Object> header = observer.getIndexHeader();
        assertEquals(observer.getIndex(), JsonUtil.getObject(header, "index").get("_index"));
    }

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule();

    @Test
    public void shouldLogConnectionError() {
        Duration maximumWaitTime = Duration.ofMinutes(2);

        Map<String, String> properties = new HashMap<>();
        properties.put("observer.elastic.elasticsearchUrl", "http://localhost:-1");
        properties.put("observer.elastic.index", "my-test-index");
        properties.put("observer.elastic.formatter.excludedMdcKeys", "excludedKey");
        properties.put("observer.elastic.maximumWaitTime", maximumWaitTime.toString());
        observer = new ElasticsearchLogEventObserver(properties, "observer.elastic");

        logEventStatusRule.setStatusLevel(StatusEvent.StatusLevel.NONE);
        observer.processBatch(new LogEventBatch().add(new LogEventSampler().build()));

        List<StatusEvent> events = LogEventStatus.getInstance().getHeadMessages(observer, StatusEvent.StatusLevel.ERROR);
        assertTrue("Expected 1 event, was " + events
                + " (all events " + LogEventStatus.getInstance().getHeadMessages() + ")", events.size() == 1);
        assertEquals("Failed to send message to " + observer.getUrl(), events.get(0).getMessage());
    }

    /**
     * To run this test, you can run Elastic Search in Docker.
     * <code>docker run -d --name elasticsearch -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" elasticsearch:7.9.2</code>
     * (elasticsearch doesn't have a :latest-tag, so explicit version is needed. I don't know if it's needed to map port 9300 as well as 9200)
     * To troubleshoot docker, run <code>docker logs elasticsearch</code>. To see the data that the test is indexing in
     * Elasticsearch, you can check out http://localhost:9200/logevents-unit-test/_search
     */
    @Test
    public void shouldPostToElastic() throws IOException {
        verifyElasticsearchConnection();

        LogEvent logEvent1 = new LogEventSampler().withRandomTime().build();
        LogEvent logEvent2 = new LogEventSampler().withRandomTime().build();

        List<String> insertedDocuments = observer.indexDocuments(new LogEventBatch().add(logEvent1).add(logEvent2));
        List<Object> insertedMessages = insertedDocuments.stream()
                .map(ExceptionUtil.softenFunctionExceptions(docUrl -> JsonParser.parseObject(new URL(observer.getUrl(), docUrl))))
                .map(o -> JsonUtil.getObject(o, "_source"))
                .map(source -> source.get("message"))
                .collect(Collectors.toList());

        assertEquals(Arrays.asList(logEvent1.getMessage(), logEvent2.getMessage()),
                insertedMessages);
    }

    void verifyElasticsearchConnection() throws IOException {
        try {
            ((HttpURLConnection) observer.getUrl().openConnection()).getResponseCode();
        } catch (ConnectException e) {
            Assume.assumeNoException("Elasticsearch is not running - try 'docker run -d --name elasticsearch -p 9200:9200 -p 9300:9300 -e \"discovery.type=single-node\" elasticsearch:7.2.0'", e);
        }
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
            actual.contains(expected));
    }

}
