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

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpServletResponseMDCTest {

    private final JsonLogEventFormatter jsonFormatter = new JsonLogEventFormatter();
    private final DynamicMDCAdapter mdcAdapter = new DynamicMDCAdapterImplementation();

    @Test
    public void shouldFormatRequestInJson() {
        HttpServletResponse mockResponse = createMockResponse();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("response", HttpServletResponseMDC.supplier(mockResponse))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);
            Map<String, Object> jsonLogEvent = jsonFormatter.toJsonObject(event);
            assertNull(jsonLogEvent.get("mdc"));
            Map<String, Object> http = JsonUtil.getObject(jsonLogEvent, "http");
            Map<String, Object> httpResponse = JsonUtil.getObject(http, "response");

            assertEquals(401, httpResponse.get("status_code"));
            assertEquals("text/html", httpResponse.get("mime_type"));
        }
    }

    @Test
    public void shouldProvideMdcForResponse() {
        HttpServletResponse mockResponse = createMockResponse();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("response", HttpServletResponseMDC.supplier(mockResponse))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);

            assertEquals(
                    " {http.response.status_code=401, http.response.mime_type=text/html}",
                    event.getMdcString(new MdcFilter.IncludedMdcKeys(new HashSet<>(Arrays.asList("http.response.status_code", "http.response.mime_type"))))
            );
        }
    }

    static HttpServletResponse createMockResponse() {
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(401);
        Mockito.when(mockResponse.getContentType()).thenReturn("text/html");
        return mockResponse;
    }

}