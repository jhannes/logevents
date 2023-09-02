package org.logevents.optional.jakarta;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.MdcFilter;
import org.logevents.formatters.JsonLogEventFormatter;
import org.logevents.mdc.DynamicMDCAdapter;
import org.logevents.mdc.DynamicMDCAdapterImplementation;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.util.JsonUtil;
import org.mockito.Mockito;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpServletRequestMDCTest {

    private final JsonLogEventFormatter jsonFormatter = new JsonLogEventFormatter();
    private final DynamicMDCAdapter mdcAdapter = new DynamicMDCAdapterImplementation();

    @Test
    public void shouldFormatRequestInJson() {
        HttpServletRequest mockRequest = createMockRequest();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("request", HttpServletRequestMDC.supplier(mockRequest))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);
            Map<String, Object> jsonLogEvent = jsonFormatter.toJsonObject(event);
            assertNull(jsonLogEvent.get("mdc"));
            assertEquals("GET", JsonUtil.getField(jsonLogEvent, "http.request.method"));
            assertEquals("http://localhost:8080/test", JsonUtil.getField(jsonLogEvent, "url.original"));
        }
    }

    @Test
    public void shouldProvideMdcForRequest() {
        HttpServletRequest mockRequest = createMockRequest();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("request", HttpServletRequestMDC.supplier(mockRequest))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);

            assertEquals(
                    " {http.request.method=GET, url.original=http://localhost:8080/test}",
                    event.getMdcString(new MdcFilter.IncludedMdcKeys(new HashSet<>(Arrays.asList("something", "url.original", "http.request.method"))))
            );
        }
    }

    @Test
    public void shouldIncludeExceptionIfAvailable() {
        HttpServletRequest mockRequest = createMockRequest();
        Throwable exception = new IOException("Here it is!");
        Mockito.when(mockRequest.getAttribute("javax.servlet.error.exception")).thenReturn(exception);

        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("request", HttpServletRequestMDC.supplier(mockRequest))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);

            Map<String, Object> jsonLogEvent = jsonFormatter.toJsonObject(event);
            assertNull(jsonLogEvent.get("mdc"));

            Map<String, Object> error = JsonUtil.getObject(jsonLogEvent, "error");
            assertEquals(exception.getClass().getName(), error.get("class"));
            assertEquals(exception.getMessage(), error.get("message"));
        }
    }

    static HttpServletRequest createMockRequest() {
        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test"));
        Mockito.when(mockRequest.getMethod()).thenReturn("GET");
        return mockRequest;
    }

}