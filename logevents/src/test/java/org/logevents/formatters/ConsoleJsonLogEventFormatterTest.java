package org.logevents.formatters;

import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConsoleJsonLogEventFormatterTest {

    private final ConsoleJsonLogEventFormatter formatter = new ConsoleJsonLogEventFormatter(new HashMap<>(), "observers.console.formatter");

    @Test
    public void shouldLogMessage() {
        String loggerName = "com.example.LoggerName";
        ZonedDateTime time = ZonedDateTime.of(2018, 8, 1, 10, 0, 0, 0, ZoneId.systemDefault());

        String message = formatter.apply(new LogEventSampler()
                .withLevel(Level.INFO)
                .withTime(time)
                .withThread("main")
                .withLoggerName(loggerName)
                .withFormat("Hello {}").withArgs("there")
                .build());
        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time);
        String hostname = new Configuration(new HashMap<>(), "").getNodeName();
        assertEquals("{\"level\": \"INFO\",\"logger\": \"com.example.LoggerName\",\"@timestamp\": \"" + timestamp + "\",\"messageFormat\": \"Hello {}\",\"thread\": \"main\",\"message\": \"Hello there\",\"marker\": \"HTTP_ERROR\",\"app\": \"logevents\",\"hostname\": \"" + hostname + "\",\"levelInt\": 20}\n",
                message);
    }


    @Test
    public void shouldDisplayException() {
        IOException throwable = new IOException("The error");
        Map<String, Object> log = JsonParser.parseObject(formatter.apply(
                new LogEventSampler()
                        .withFormat("Log message")
                        .withThrowable(throwable)
                        .build())
        );
        assertEquals("Log message " + throwable, log.get("message"));
        assertEquals(IOException.class.getName(), log.get("exception.class"));
        assertEquals(throwable.getMessage(), log.get("exception.message"));
    }

    @Test
    public void shouldDisplayMdc() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.console.includedMdcKeys", "operation,user");
        Configuration configuration = new Configuration(properties, "observer.console");
        formatter.configure(configuration);
        configuration.checkForUnknownFields();

        String message = formatter.apply(new LogEventSampler()
                .withMdc("operation", "op13")
                .withMdc("user", "userOne")
                .withMdc("secret", "secret value")
                .build());
        Map<String, Object> mdc = JsonUtil.getObject(JsonParser.parseObject(message), "mdc");
        assertEquals("op13", mdc.get("operation"));
        assertEquals("userOne", mdc.get("user"));
        assertNull(mdc.get("secret"));
    }

    @Test
    public void shouldExcludeMdc() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.console.excludedMdcKeys", "secret");
        Configuration configuration = new Configuration(properties, "observer.console");
        formatter.configure(configuration);
        configuration.checkForUnknownFields();

        String message = formatter.apply(new LogEventSampler()
                .withMdc("operation", "op13")
                .withMdc("user", "userOne")
                .withMdc("secret", "secret value")
                .build());
        Map<String, Object> mdc = JsonUtil.getObject(JsonParser.parseObject(message), "mdc");
        assertEquals("op13", mdc.get("operation"));
        assertEquals("userOne", mdc.get("user"));
        assertNull(mdc.get("secret"));
    }

    @Test
    public void shouldIncludeBaseProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.console.formatter.properties.environment", "staging");
        properties.put("observer.console.formatter.properties.dataCenter", "norway-east");
        Configuration configuration = new Configuration(properties, "observer.console.formatter");
        formatter.configure(configuration);
        configuration.checkForUnknownFields();

        Map<String, Object> json = JsonParser.parseObject(formatter.apply(new LogEventSampler().build()));
        assertEquals("staging", json.get("environment"));
        assertEquals("norway-east", json.get("dataCenter"));
    }
}
