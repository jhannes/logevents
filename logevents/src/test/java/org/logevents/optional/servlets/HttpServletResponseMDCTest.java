package org.logevents.optional.servlets;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.MdcFilter;
import org.logevents.formatters.JsonLogEventFormatter;
import org.logevents.mdc.DynamicMDCAdapter;
import org.logevents.mdc.DynamicMDCAdapterImplementation;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.util.JsonUtil;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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

    @Test
    public void shouldClassifyMarkerBasedOnResponseCode() {
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(500);
        assertEquals(HttpServletResponseMDC.HTTP_ERROR, HttpServletResponseMDC.getMarker(mockResponse));
        Mockito.when(mockResponse.getStatus()).thenReturn(307);
        assertEquals(HttpServletResponseMDC.REDIRECT, HttpServletResponseMDC.getMarker(mockResponse));
        Mockito.when(mockResponse.getStatus()).thenReturn(304);
        assertEquals(HttpServletResponseMDC.NOT_MODIFIED, HttpServletResponseMDC.getMarker(mockResponse));
    }

    @Test
    public void shouldClassifyMarkerBasedOnContentType() {
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(200);
        assertEquals(HttpServletResponseMDC.HTTP, HttpServletResponseMDC.getMarker(mockResponse));
        Mockito.when(mockResponse.getContentType()).thenReturn("image/png");
        assertEquals(HttpServletResponseMDC.ASSET, HttpServletResponseMDC.getMarker(mockResponse));
        Mockito.when(mockResponse.getContentType()).thenReturn("application/json");
        assertEquals(HttpServletResponseMDC.JSON, HttpServletResponseMDC.getMarker(mockResponse));
    }


    static HttpServletResponse createMockResponse() {
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(401);
        Mockito.when(mockResponse.getContentType()).thenReturn("text/html");
        return mockResponse;
    }

}