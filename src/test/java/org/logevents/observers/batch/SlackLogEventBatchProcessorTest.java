package org.logevents.observers.batch;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.logevents.LogEvent;
import org.slf4j.event.Level;

@SuppressWarnings("restriction")
public class SlackLogEventBatchProcessorTest {

    public static class Factory extends SlackLogMessageFactory {
        @Override
        public Map<String, Object> createSlackMessage(List<LogEventGroup> batch, Optional<String> username, Optional<String> channel) {
            Map<String, Object> message = new HashMap<>();
            LogEvent event = batch.get(0).headMessage();
            message.put("text", event.formatMessage());
            return message;
        }
    }

    private List<String> buffer = new ArrayList<>();

    @Test
    public void shouldSendSlackMessage() throws IOException {
        HttpServer server = startServer();
        int port = server.getAddress().getPort();

        Properties properties = new Properties();
        properties.setProperty("observer.slack.processor.slackUrl", "http://localhost:" + port);
        properties.setProperty("observer.slack.processor.slackLogMessageFactory", Factory.class.getName());
        SlackLogEventBatchProcessor processor = new SlackLogEventBatchProcessor(properties, "observer.slack.processor");

        LogEvent logEvent = new LogEvent("org.example", Level.WARN, "Nothing");
        processor.processBatch(Arrays.asList(new LogEventGroup(logEvent)));

        assertEquals(Arrays.asList("{\n  \"text\": \"Nothing\"\n}"),
                buffer);
    }

    private HttpServer startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", t -> {
            buffer.add(toString(t.getRequestBody()));
            t.sendResponseHeaders(200, 0);
            t.close();
        });
        server.start();
        return server;
    }

    private String toString(InputStream input) {
        return new BufferedReader(new InputStreamReader(input))
                .lines().collect(Collectors.joining("\n"));
    }
}
