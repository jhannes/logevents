package org.logevents.observers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.optional.junit.LogEventStatusRule;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.logevents.util.ExceptionUtil;
import org.logevents.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HumioLogEventObserverTest {

    private static final String EXAMPLE_AUTHORIZATION_HEADER_VALUE = "foo bar";
    private static final String EXAMPLE_CONFIG_PREFIX = "observer.humio";
    private static final int INVALID_PORT = -1;
    private final List<String> requestBodyBuffer = new ArrayList<>();
    private final List<String> requestPathBuffer = new ArrayList<>();
    private final List<Headers> requestHeaderBuffer = new ArrayList<>();

    private HumioLogEventObserver observer = new HumioLogEventObserver(defaultConfigurationMap(
            extractPortNumberForMockHumioServer(successfulHumioResponse())), EXAMPLE_CONFIG_PREFIX);

    private Map<String, String> defaultConfigurationMap(int portNumberForElasticsearchUrl) {
        Map<String, String> config = new HashMap<>();
        config.put(EXAMPLE_CONFIG_PREFIX + ".elasticsearchUrl", "http://localhost:" + portNumberForElasticsearchUrl);
        config.put(EXAMPLE_CONFIG_PREFIX + ".index", "logevents-unit-test");
        return config;
    }

    private Map<String, String> defaultConfigurationWithAuthorization(int portNumberForElasticsearchurl, String headerValue) {
        Map<String, String> config = defaultConfigurationMap(portNumberForElasticsearchurl);
        config.put(EXAMPLE_CONFIG_PREFIX + ".elasticsearchAuthorizationHeader", headerValue);
        return config;
    }

    @Test
    public void shouldFormatSimpleLogEvent() {
        LogEvent event = new LogEventSampler().withMarker().build();
        Map<String, Object> payload = observer.formatMessage(event);

        assertEquals(event.getInstant(), ZonedDateTime.parse(payload.get("@timestamp").toString()).toInstant());
        assertEquals(event.getLoggerName(), payload.get("log.logger"));
        assertEquals(event.getLevel().name(), payload.get("log.level"));
        assertEquals(event.getMessage(), payload.get("message"));
        assertEquals(event.getThreadName(), payload.get("process.thread.name"));
        assertEquals(Collections.singletonList(event.getMarker().getName()), payload.get("tags"));
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

        Map<String, Object> error = JsonUtil.getObject(payload, "error");
        assertEquals(event.getThrowable().getClass().getName(), error.get("class"));
        assertEquals(event.getThrowable().getMessage(), error.get("message"));
        assertContains("at org.logeventsdemo.internal.MyClassName.internalMethod(MyClassName.java:311)",
                error.get("stack_trace").toString());
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
        properties.put(EXAMPLE_CONFIG_PREFIX + ".elasticsearchUrl", "http://localhost:-1");
        properties.put(EXAMPLE_CONFIG_PREFIX + ".index", "my-test-index");
        properties.put(EXAMPLE_CONFIG_PREFIX + ".formatter.excludedMdcKeys", "excludedKey");
        properties.put(EXAMPLE_CONFIG_PREFIX + ".maximumWaitTime", maximumWaitTime.toString());
        observer = new HumioLogEventObserver(properties, EXAMPLE_CONFIG_PREFIX);

        logEventStatusRule.setStatusLevel(StatusEvent.StatusLevel.NONE);
        observer.processBatch(new LogEventBatch().add(new LogEventSampler().build()));

        List<StatusEvent> events = LogEventStatus.getInstance().getMessages(observer, StatusEvent.StatusLevel.ERROR);
        assertEquals("Expected 1 event, was " + events
                     + " (all events " + LogEventStatus.getInstance().getHeadMessages() + ")", 1, events.size());
        assertEquals("Failed to send message to " + observer.getUrl(), events.get(0).getMessage());
    }


    @Test
    public void shouldPostToHumio() throws IOException {
        observer = setupMockServerAndSystemUnderTest(successfulHumioResponse());

        LogEvent logEvent1 = new LogEventSampler().withRandomTime().build();
        LogEvent logEvent2 = new LogEventSampler().withRandomTime().build();

        List<String> insertedDocuments = observer.indexDocuments(new LogEventBatch().add(logEvent1).add(logEvent2));
        assertEquals("Because Humio API does not return document ids we can fetch from humiosearch..", insertedDocuments.size(), 0);
    }

    @Test
    public void indexDocumentsDoesIncludeAuthroizationIfProvided() throws IOException {
        observer = setupMockServerAndSystemUnderTest(successfulHumioResponse());

        LogEvent logEvent1 = new LogEventSampler().withRandomTime().build();
        LogEvent logEvent2 = new LogEventSampler().withRandomTime().build();

        observer.indexDocuments(new LogEventBatch().add(logEvent1).add(logEvent2));

        Optional<String> expectedAuthorizationHeader = requestHeaderBuffer.stream()
                .filter(h -> h.containsKey("Authorization"))
                .map(h -> h.getFirst("Authorization"))
                .findFirst();
        assertTrue("Authorization header to be present in the request", expectedAuthorizationHeader.isPresent());
        assertEquals(EXAMPLE_AUTHORIZATION_HEADER_VALUE, expectedAuthorizationHeader.get());
    }

    private HumioLogEventObserver setupMockServerAndSystemUnderTest(byte[] response) {
        return new HumioLogEventObserver(
                defaultConfigurationWithAuthorization(extractPortNumberForMockHumioServer(response),
                        EXAMPLE_AUTHORIZATION_HEADER_VALUE), EXAMPLE_CONFIG_PREFIX);
    }

    @Test
    public void indexDocumentShouldLogErrorsWhenHumioResponseContainingErrors() {
        observer = setupMockServerAndSystemUnderTest(partlyErronousHumioResponse());

        LogEvent logEvent1 = new LogEventSampler().withRandomTime().build();
        LogEvent logEvent2 = new LogEventSampler().withRandomTime().build();

        IOException e = assertThrows(IOException.class, () ->
                observer.indexDocuments(new LogEventBatch().add(logEvent1).add(logEvent2)));
        assertEquals(e.getMessage(), "Failed sending 1 out of 4 entries");
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

    private int extractPortNumberForMockHumioServer(byte[] response) {
        try {
            HttpServer server = startServer(t -> {
                requestBodyBuffer.add(toString(t.getRequestBody()));
                requestPathBuffer.add(t.getHttpContext().getPath());
                requestHeaderBuffer.add(t.getRequestHeaders());
                t.sendResponseHeaders(200, 0);
                t.getResponseBody().write(response);
                t.getResponseBody().flush();
                t.close();
            });
            return server.getAddress().getPort();
        } catch (IOException e) {
            ExceptionUtil.softenException(e);
        }
        return INVALID_PORT;
    }

    private Object expectIndexObject() {
        Map<String, Object> indexObjectRoot = new HashMap<>();
        Map<String, Object> indexEntry = new HashMap<>();
        indexEntry.put("_index", "logevents-unit-test");
        indexEntry.put("index", indexEntry);
        return indexObjectRoot;
    }

    private byte[] successfulHumioResponse() {
        Map<String, Object> humioResponse = new HashMap<>();

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createItem(200));
        items.add(createItem(200));
        items.add(createItem(200));
        items.add(createItem(200));
        humioResponse.put("items", items);
        humioResponse.put("errors", "false");
        return JsonUtil.toIndentedJson(humioResponse).getBytes(StandardCharsets.UTF_8);
    }


    private byte[] partlyErronousHumioResponse() {
        Map<String, Object> humioResponse = new HashMap<>();

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createItem(200));
        items.add(createItem(200));
        items.add(createItem(400));
        items.add(createItem(200));
        humioResponse.put("items", items);
        humioResponse.put("errors", "true");

        return JsonUtil.toIndentedJson(humioResponse).getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> createItem(int httpStatusCode) {
        return Collections.singletonMap("create", Collections.singletonMap("status", httpStatusCode));
    }

    private HttpServer startServer(HttpHandler httpHandler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", httpHandler);
        server.start();
        return server;
    }

    private String toString(InputStream input) {
        return new BufferedReader(new InputStreamReader(input))
                .lines().collect(Collectors.joining("\n"));
    }
}
