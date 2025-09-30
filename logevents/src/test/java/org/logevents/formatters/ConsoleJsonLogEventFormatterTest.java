package org.logevents.formatters;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.mdc.DynamicMDC;
import org.logevents.mdc.DynamicMDCAdapter;
import org.logevents.mdc.DynamicMDCAdapterImplementation;
import org.logevents.mdc.ExceptionMDC;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
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
                .withMarker(LogEventSampler.HTTP_ERROR)
                .build());
        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time);
        assertEquals("{\"log.level\":\"INFO\",\"log.logger\":\"com.example.LoggerName\",\"@timestamp\":\"" + timestamp + "\",\"messageFormat\":\"Hello {}\",\"message\":\"Hello there\",\"tags\":[\"HTTP_ERROR\"],\"process.thread.name\":\"main\",\"process.thread.group\":\"main\"}\n",
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
        Map<String, Object> error = JsonUtil.getObject(log, "error");
        assertEquals(IOException.class.getName(), error.get("class"));
        assertEquals(throwable.getMessage(), error.get("message"));
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

    @Test
    public void shouldFormatExceptionInDynamicMDC() {
        IOException throwable = new IOException("The error");
        DynamicMDCAdapter mdcAdapter = new DynamicMDCAdapterImplementation();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("exception", () -> new ExceptionMDC(throwable))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);
            Map<String, Object> jsonLogEvent = formatter.toJsonObject(event);
            assertNull(jsonLogEvent.get("mdc"));
            Map<String, Object> jsonError = JsonUtil.getObject(jsonLogEvent, "error");
            assertEquals(throwable.getClass().getName(), JsonUtil.getField(jsonError, "class"));
            assertEquals(throwable.getMessage(), JsonUtil.getField(jsonError, "message"));
        }
    }

    @Test
    public void shouldFormatDynamicMdcVariables() {
        DynamicMDCAdapter mdcAdapter = new DynamicMDCAdapterImplementation();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("test", () -> DynamicMDC.ofMap(() -> Collections.singletonMap("key", "value")))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);
            Map<String, Object> jsonLogEvent = formatter.toJsonObject(event);
            assertEquals("value", JsonUtil.getObject(jsonLogEvent, "mdc").get("key"));
        }
    }

    @Test
    public void shouldOutputKeyValuePairs() {
        LogEvent event = new LogEventSampler().build();
        event.getKeyValuePairs().add(new KeyValuePair("k1", "v1"));
        event.getKeyValuePairs().add(new KeyValuePair("k2", 200));
        event.getKeyValuePairs().add(new KeyValuePair("k3", false));
        event.getKeyValuePairs().add(new KeyValuePair("k4", LocalDate.of(2025, 9, 30)));
        assertEquals(
                "{\"k1\":\"v1\",\"k2\":200,\"k3\":false,\"k4\":\"2025-09-30\"}",
                JsonUtil.toCompactJson(JsonUtil.getObject(formatter.toJsonObject(event), "keyValuePairs"))
        );
    }

}
