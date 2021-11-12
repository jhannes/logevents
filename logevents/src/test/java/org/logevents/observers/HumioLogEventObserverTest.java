package org.logevents.observers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
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
import org.logevents.util.JsonUtil;

public class HumioLogEventObserverTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9200);

    private HumioLogEventObserver observer = new HumioLogEventObserver(toURL("http://localhost:9200"), "logevents-unit-test");

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
        MatcherAssert.assertThat(payload.get("exception.stackTrace").toString(),
            containsString("at org.logeventsdemo.internal.MyClassName.internalMethod(MyClassName.java:311)"));
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
        properties.put("observer.humio.elasticsearchUrl", "http://localhost:-1");
        properties.put("observer.humio.index", "my-test-index");
        properties.put("observer.humio.formatter.excludedMdcKeys", "excludedKey");
        properties.put("observer.humio.maximumWaitTime", maximumWaitTime.toString());
        observer = new HumioLogEventObserver(properties, "observer.humio");

        logEventStatusRule.setStatusLevel(StatusEvent.StatusLevel.NONE);
        observer.processBatch(new LogEventBatch().add(new LogEventSampler().build()));

        List<StatusEvent> events = LogEventStatus.getInstance().getHeadMessages(observer, StatusEvent.StatusLevel.ERROR);
        assertTrue("Expected 1 event, was " + events
            + " (all events " + LogEventStatus.getInstance().getHeadMessages() + ")", events.size() == 1);
        assertEquals("Failed to send message to " + observer.getUrl(), events.get(0).getMessage());
    }


    @Test
    public void shouldPostTohumio() throws IOException {
        givenHumioServerRespondsSuccessfully();

        LogEvent logEvent1 = new LogEventSampler().withRandomTime().build();
        LogEvent logEvent2 = new LogEventSampler().withRandomTime().build();

        List<String> insertedDocuments = observer.indexDocuments(new LogEventBatch().add(logEvent1).add(logEvent2));
        assertEquals("Because Humio API does not return document ids we can fetch from humiosearch..", insertedDocuments.size(), 0);
    }

    @Test
    public void indexDocumentsDoesIncludeAuthroizationIfProvided() throws IOException {
        observer = new HumioLogEventObserver(toURL("http://localhost:9200"), "foo bar", "logevents-unit-test");
        givenHumioServerRespondsSuccessfullyWithCredentials();

        LogEvent logEvent1 = new LogEventSampler().withRandomTime().build();
        LogEvent logEvent2 = new LogEventSampler().withRandomTime().build();

        List<String> insertedDocuments = observer.indexDocuments(new LogEventBatch().add(logEvent1).add(logEvent2));
        assertEquals("Because Humio API does not return document ids we can fetch from humiosearch..", insertedDocuments.size(), 0);
    }


    @Test
    public void indexDocumentShouldLogErrorsWhenHumioResponseContainingErrors() throws IOException {
        givenHumioServerRespondsWithErrors();

        LogEvent logEvent1 = new LogEventSampler().withRandomTime().build();
        LogEvent logEvent2 = new LogEventSampler().withRandomTime().build();

        IOException e = assertThrows(IOException.class, () ->
            observer.indexDocuments(new LogEventBatch().add(logEvent1).add(logEvent2)));
        assertEquals(e.getMessage(), "Failed sending 1 out of 4 entries");
    }

    private void givenHumioServerRespondsSuccessfully() {
        Map<String, Object> humioResponse = successfulHumioResponse();

        wireMockRule.givenThat(post(urlEqualTo("/api/v1/ingest/elastic-bulk"))
                .willReturn(aResponse().withStatus(200).withBody(JsonUtil.toIndentedJson(humioResponse))));
    }

    private Map<String, Object> successfulHumioResponse() {
        Map<String, Object> humioResponse = new HashMap<>();

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createItem(200));
        items.add(createItem(200));
        items.add(createItem(200));
        items.add(createItem(200));
        humioResponse.put("items", items);
        humioResponse.put("errors", "false");
        return humioResponse;
    }

    private void givenHumioServerRespondsSuccessfullyWithCredentials() {
        Map<String, Object> humioResponse = successfulHumioResponse();

        wireMockRule.givenThat(post(urlEqualTo("/api/v1/ingest/elastic-bulk"))
            .withHeader("Authorization", equalTo("foo bar"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtil.toIndentedJson(humioResponse))));
    }

    private void givenHumioServerRespondsWithErrors() {
        Map<String, Object> humioResponse = new HashMap<>();

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createItem(200));
        items.add(createItem(200));
        items.add(createItem(400));
        items.add(createItem(200));
        humioResponse.put("items", items);
        humioResponse.put("errors", "true");

        wireMockRule.givenThat(post(urlEqualTo("/api/v1/ingest/elastic-bulk"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtil.toIndentedJson(humioResponse))));
    }

    private Map<String, Object> createItem(int httpStatusCode) {
        return Collections.singletonMap("create", Collections.singletonMap("status", httpStatusCode));
    }



}