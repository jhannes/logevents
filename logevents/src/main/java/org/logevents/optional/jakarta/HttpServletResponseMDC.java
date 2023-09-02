package org.logevents.optional.jakarta;

import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.mdc.DynamicMDC;

import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Populates MDC or JSON format according to <a href="https://www.elastic.co/guide/en/ecs/current/">Elastic
 * Common Schema</a> guidelines:
 *
 * <ul>
 * <li><strong>http.response.status_code</strong>: {@link HttpServletResponse#getStatus()}</li>
 * <li><strong>http.response.mime_type</strong>: {@link HttpServletResponse#getContentType()}</li>
 * <li><strong>http.response.redirect</strong>: "Location" response header</li>
 * </ul>
 */
public class HttpServletResponseMDC implements DynamicMDC {

    public static void populateJson(Map<String, Object> jsonPayload, HttpServletResponse response) {
        Map<String, Object> http = new HashMap<>();

        Map<String, Object> httpResponse = new HashMap<>();
        httpResponse.put("status_code", response.getStatus());
        httpResponse.put("mime_type", response.getContentType());
        httpResponse.put("redirect", response.getHeader("Location"));

        http.put("response", httpResponse);
        jsonPayload.put("http", http);
    }

    public static void addMdcVariables(Map<String, String> result, HttpServletResponse response) {
        result.put("http.response.status_code", String.valueOf(response.getStatus()));
        result.put("http.response.mime_type", response.getContentType());
        result.put("http.response.redirect", response.getHeader("Location"));
    }

    private final HttpServletResponse response;

    public HttpServletResponseMDC(HttpServletResponse response) {
        this.response = response;
    }

    public static Supplier<DynamicMDC> supplier(HttpServletResponse response) {
        return () -> new HttpServletResponseMDC(response);
    }

    @Override
    public Iterable<? extends Map.Entry<String, String>> entrySet() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        addMdcVariables(result, response);
        return result.entrySet();
    }

    @Override
    public void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
        populateJson(jsonPayload, response);
    }
}
