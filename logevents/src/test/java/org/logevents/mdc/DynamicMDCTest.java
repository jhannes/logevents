package org.logevents.mdc;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.optional.junit.LogEventSampler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DynamicMDCTest {

    private final DynamicMDCAdapter mdcAdapter = new DynamicMDCAdapterImplementation();

    @Test
    public void shouldPutDynamicVariables() {
        //List<String> values = new ArrayList<>();
        List<String> values = Collections.singletonList("test");
        try (DynamicMDCAdapter.Cleanup ignored = DynamicMDC.putEntry("mdcTest", values::toString)) {
            //LogEvent logEvent = new LogEvent(null, null, null, null, new Object[0]);
            //assertEquals("[]", logEvent.getMdc("mdcTest", null));
            //values.add("test");
            LogEvent newEvent = new LogEvent(null, null, null, null, new Object[0]);
            assertEquals("[test]", newEvent.getMdc("mdcTest", null));
        }
    }

    @Test
    public void shouldUpdateDynamicVariables() {
        String key = "test";
        Map<String, String> value = new HashMap<>();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic(key, () -> DynamicMDC.ofMap(() -> value))) {
            assertNull(mdcAdapter.get(key));
            assertNull(new LogEventSampler().build(mdcAdapter).getMdc("hello", null));

            value.put("hello", "world");
            assertEquals("world", new LogEventSampler().build(mdcAdapter).getMdc("hello", null));

            value.put("hello", "updated");
            assertEquals("updated", new LogEventSampler().build(mdcAdapter).getMdc("hello", null));
        }
        LogEvent logEvent = new LogEventSampler().build(mdcAdapter);
        assertNull(logEvent.getMdc("hello", null));
    }

    @Test
    public void shouldGetCopyOfContextMap() {
        Map<String, String> value = new HashMap<>();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("test", () -> DynamicMDC.ofMap(() -> value))) {
            assertNull(mdcAdapter.getCopyOfContextMap());

            mdcAdapter.put("something", "some value");
            assertEquals("some value", mdcAdapter.getCopyOfContextMap().get("something"));

            value.put("otherThing", "other value");
            assertEquals("other value", mdcAdapter.getCopyOfContextMap().get("otherThing"));

            mdcAdapter.remove("something");
            assertEquals("other value", mdcAdapter.getCopyOfContextMap().get("otherThing"));
            assertNull(mdcAdapter.getCopyOfContextMap().get("something"));
        }
    }

    @Test
    public void shouldClearVariables() {
        Map<String, String> value = new HashMap<>();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("test", () -> DynamicMDC.ofMap(() -> value))) {
            value.put("hello", "world");
            assertEquals("world", new LogEventSampler().build(mdcAdapter).getMdc("hello", null));

            mdcAdapter.clear();
            assertNull(new LogEventSampler().build(mdcAdapter).getMdc("hello", null));
        }
    }

    @Test
    public void shouldRetainVariables() {
        Map<String, String> value = new HashMap<>();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("test", () -> DynamicMDC.ofMap(() -> value)).retainIfIncomplete()) {
            value.put("hello", "world");
            assertEquals("world", new LogEventSampler().build(mdcAdapter).getMdc("hello", null));
        }
        assertEquals("world", new LogEventSampler().build(mdcAdapter).getMdc("hello", null));
    }

    @Test
    public void shouldClearCompletedVariables() {
        Map<String, String> value = new HashMap<>();
        try (DynamicMDCAdapter.Cleanup cleanup = mdcAdapter.putDynamic("test", () -> DynamicMDC.ofMap(() -> value)).retainIfIncomplete()) {
            value.put("hello", "world");
            assertEquals("world", new LogEventSampler().build(mdcAdapter).getMdc("hello", null));
            cleanup.complete();
        }
        assertNull(new LogEventSampler().build(mdcAdapter).getMdc("hello", null));
    }

    @Test
    public void shouldSupportNullValues() {
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("test", () -> null)) {
            assertNull(new LogEventSampler().build(mdcAdapter).getMdc("hello", null));
        }
    }

    @Test
    public void shouldMergeDynamicAndRegularMDCValues() {
        mdcAdapter.put("other", "value");
        String key = "test";
        Map<String, String> value = new HashMap<>();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic(key, () -> DynamicMDC.ofMap(() -> value))) {
            LogEvent logEvent = new LogEventSampler().build(mdcAdapter);
            assertNull(logEvent.getMdc("hello", null));
            assertEquals("value", logEvent.getMdc("other", null));

            value.put("hello", "world");
            LogEvent updatedEvent = new LogEventSampler().build(mdcAdapter);
            assertEquals("world", updatedEvent.getMdc("hello", null));
            assertEquals("value", updatedEvent.getMdc("other", null));
        }
    }

    @Test
    public void shouldUpdateJsonValues() {
        Map<String, String> value = new HashMap<>();

        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("something", () -> new DemoOfJsonUpdate("key1", value))) {
            try (DynamicMDCAdapter.Cleanup ignored2 = mdcAdapter.putDynamic("somethingElse", () -> new DemoOfJsonUpdate("key2", value))) {

                value.put("test1", "test2");
                value.put("test3", "test4");

                LogEvent logEvent = new LogEventSampler().build(mdcAdapter);

                Map<String, Object> json = new HashMap<>();
                logEvent.populateJson(json);

                assertEquals(value, json.get("key1"));
                assertEquals(value, json.get("key2"));
            }
        }
    }

    static class DemoOfJsonUpdate implements DynamicMDC {

        private final String jsonProperty;
        private final Map<String, String> value;

        public DemoOfJsonUpdate(String jsonProperty, Map<String, String> value) {
            this.jsonProperty = jsonProperty;
            this.value = value;
        }

        @Override
        public Iterable<? extends Map.Entry<String, String>> entrySet() {
            return Collections.emptyList();
        }

        @Override
        public void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
            jsonPayload.put(jsonProperty, value);
        }
    }

}