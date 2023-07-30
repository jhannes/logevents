package org.logevents.mdc;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.optional.junit.LogEventSampler;
import org.slf4j.helpers.BasicMDCAdapter;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DynamicMDCBasicDelegatorTest {

    private final DynamicMDCAdapter delegator = new DynamicMDCBasicDelegator(new BasicMDCAdapter());

    @Test
    public void shouldSupportDynamicMDCwithDefaultSlf4jImplementation() {
        String key = "test";
        String value = "value";
        try (DynamicMDCAdapter.Cleanup ignored = delegator.putDynamic(key, () -> DynamicMDC.ofMap(() -> Collections.singletonMap(key, value)))) {
            assertEquals(value, delegator.get(key));

            LogEvent logEvent = new LogEventSampler().build(delegator);
            assertEquals(value, logEvent.getMdc(key, null));
        }
        LogEvent logEvent = new LogEventSampler().build(delegator);
        assertNull(logEvent.getMdc(key, null));
    }

    @Test
    public void shouldSupportClearingMdcVariables() {
        String key = "test";
        String value = "value";
        try (DynamicMDCAdapter.Cleanup ignored = delegator.putDynamic(key, () -> DynamicMDC.ofMap(() -> Collections.singletonMap(key, value)))) {
            delegator.clear();
            assertNull(delegator.get(key));

            LogEvent logEvent = new LogEventSampler().build(delegator);
            assertNull(logEvent.getMdc(key, null));
        }
    }

    @Test
    public void shouldSupportEmptyDynamicMdc() {
        delegator.put("other", "some other value");
        String key = "test";
        try (DynamicMDCAdapter.Cleanup ignored = delegator.putDynamic(key, () -> null)) {
            LogEvent logEvent = new LogEventSampler().build(delegator);
            assertNull(logEvent.getMdc(key, null));
            assertEquals("some other value", logEvent.getMdc("other", null));
        }
    }

}