package org.logevents.optional.jakarta;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.formatters.JsonLogEventFormatter;
import org.logevents.mdc.DynamicMDCAdapter;
import org.logevents.mdc.DynamicMDCAdapterImplementation;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.util.JsonUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.logevents.optional.jakarta.HttpServletRequestMDCTest.createMockRequest;
import static org.logevents.optional.jakarta.HttpServletResponseMDCTest.createMockResponse;

public class HttpServletMDCTest {

    private final JsonLogEventFormatter jsonFormatter = new JsonLogEventFormatter();
    private final DynamicMDCAdapter mdcAdapter = new DynamicMDCAdapterImplementation();

    @Test
    public void shouldFormatRequestAndResponseInJson() {
        HttpServletRequest mockRequest = createMockRequest();
        HttpServletResponse mockResponse = createMockResponse();
        try (DynamicMDCAdapter.Cleanup ignored = mdcAdapter.putDynamic("request", HttpServletMDC.supplier(mockRequest, mockResponse))) {
            LogEvent event = new LogEventSampler().build(mdcAdapter);

            Map<String, Object> jsonLogEvent = jsonFormatter.toJsonObject(event);

            Map<String, Object> http = JsonUtil.getObject(jsonLogEvent, "http");
            Map<String, Object> httpResponse = JsonUtil.getObject(http, "response");
            assertEquals(mockResponse.getStatus(), httpResponse.get("status_code"));
            assertEquals(mockResponse.getContentType(), httpResponse.get("mime_type"));

            assertEquals("GET", JsonUtil.getField(http, "request.method"));
        }
    }

}
