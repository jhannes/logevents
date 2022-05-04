package org.logevents.observers;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatcher;
import org.logevents.observers.slack.SlackLogEventsFormatter;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.logevents.status.StatusEvent.StatusLevel;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("restriction")
public class SlackLogEventObserverTest {

    public static class Formatter extends SlackLogEventsFormatter {
        @Override
        protected List<Map<String, Object>> createDetailsField(LogEvent event) {
            return null;
        }

        @Override
        protected String createText(LogEvent event) {
            return event.getMessage();
        }
    }

    private final List<String> buffer = new ArrayList<>();

    @Test
    public void shouldSendSlackMessage() throws IOException {
        HttpServer server = startServer(t -> {
                    buffer.add(toString(t.getRequestBody()));
                    t.sendResponseHeaders(200, 0);
                    t.close();
                });
        int port = server.getAddress().getPort();

        Map<String, String> properties = new HashMap<>();
        properties.put("observer.slack.slackUrl", "http://localhost:" + port);
        properties.put("observer.slack.formatter", Formatter.class.getName());
        properties.put("observer.slack.username", "loguser");
        properties.put("observer.slack.channel", "general");

        SlackLogEventObserver observer = new SlackLogEventObserver(properties, "observer.slack");

        LogEvent logEvent = new LogEventSampler().withMarker(null).withLevel(Level.WARN).withFormat("Nothing").build();
        observer.processBatch(new LogEventBatch().add(logEvent));

        assertEquals(singletonList("{\n" +
                        "  \"username\": \"loguser\",\n" +
                        "  \"channel\": \"general\",\n" +
                        "  \"attachments\": [\n" +
                        "    {\n" +
                        "      \"color\": \"warning\",\n" +
                        "      \"text\": \"Nothing\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}"),
            buffer);
    }

    @Test
    public void shouldHandleErrors() throws IOException {
        LogEventStatus.getInstance().setThreshold(StatusLevel.FATAL);
        HttpServer server = startServer(t -> {
            t.sendResponseHeaders(400, 0);
            t.getResponseBody().write("A detailed error message".getBytes());
            t.getResponseBody().flush();
            t.close();
        });
        int port = server.getAddress().getPort();

        URL url = new URL("http://localhost:" + port);
        SlackLogEventObserver observer = new SlackLogEventObserver(url, Optional.empty(), Optional.empty());
        LogEvent logEvent = new LogEventSampler().build();
        observer.processBatch(new LogEventBatch().add(logEvent));

        List<StatusEvent> events = LogEventStatus.getInstance().getMessages(observer, StatusLevel.ERROR);
        assertTrue("Expected 1 event, was " + events, events.size() == 1);
        assertEquals("Failed to send message to " + url, events.get(0).getMessage());
        assertEquals("Failed to POST to " + url + ", status code: 400: A detailed error message",
                events.get(0).getThrowable().getMessage());
    }

    @Test
    public void shouldConfigureSlackObserver() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.slack.slackUrl", "http://localhost:1234");
        properties.put("observer.slack.channel", "general");
        properties.put("observer.slack.username", "MyTestApp");
        properties.put("observer.slack.formatter.sourceCode.1.package", "org.logevents");
        properties.put("observer.slack.formatter.sourceCode.1.maven", "org.logevents/logevents");
        properties.put("observer.slack.formatter.sourceCode.2.package", "org.junit");
        properties.put("observer.slack.formatter.sourceCode.2.github", "https://github.com/junit-team/junit4");

        SlackLogEventObserver observer = new SlackLogEventObserver(properties, "observer.slack");

        assertEquals("SlackLogEventObserver{formatter=SlackLogEventsFormatter{username=MyTestApp,channel=general},url=http://localhost:1234}",
                observer.toString());
    }

    @Test
    public void shouldConfigureMarkerBatcher() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.slack.slackUrl", "http://localhost:1234");
        properties.put("observer.slack.markers.FOO.throttle", "PT30S");
        properties.put("observer.slack.markers.FOO.channel", "fooMessages");

        SlackLogEventObserver observer = new SlackLogEventObserver(properties, "observer.slack") {
            @Override
            protected String postJson(Map<String, Object> jsonMessage) {
                assertEquals("fooMessages", jsonMessage.get("channel"));
                return "ok";
            }
        };
        Marker marker = MarkerFactory.getMarker("FOO");
        LogEvent event = new LogEventSampler().withMarker(marker).build();
        observer.logEvent(event);
        ((LogEventBatcher) observer.getBatcher(event)).flush();
    }

    @Test
    public void shouldConfigureProxy() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.slack.slackUrl", "http://localhost:1234");
        properties.put("observer.slack.proxy", "proxy.example.org:8888");

        SlackLogEventObserver observer = new SlackLogEventObserver(properties, "observer.slack");
        assertEquals(new InetSocketAddress("proxy.example.org", 8888), observer.getProxy().address());
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
