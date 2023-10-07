package org.logevents.optional.servlets;

import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.mdc.DynamicMDC;
import org.logevents.mdc.DynamicMDCAdapter;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
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

    protected static final Marker HTTP = MarkerFactory.getMarker("HTTP");
    protected static final Marker HTTP_ERROR = MarkerFactory.getMarker("HTTP_ERROR");
    protected static final Marker ASSET = MarkerFactory.getMarker("HTTP_ASSET");
    protected static final Marker JSON = MarkerFactory.getMarker("HTTP_JSON");
    protected static final Marker XML = MarkerFactory.getMarker("HTTP_XML");
    protected static final Marker REDIRECT = MarkerFactory.getMarker("HTTP_REDIRECT");
    protected static final Marker NOT_MODIFIED = MarkerFactory.getMarker("HTTP_NOT_MODIFIED");
    protected static final Marker STATUS = MarkerFactory.getMarker("HTTP_STATUS_REQUEST");
    static {
        HTTP_ERROR.add(HTTP);
        ASSET.add(HTTP);
        JSON.add(HTTP);
        REDIRECT.add(HTTP);
        NOT_MODIFIED.add(HTTP);
        NOT_MODIFIED.add(ASSET);
        STATUS.add(HTTP);
    }

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
        if (response.getContentType() != null) {
            result.put("http.response.mime_type", response.getContentType());
        }
        if (response.getHeader("Location") != null) {
            result.put("http.response.redirect", response.getHeader("Location"));
        }
    }

    private final HttpServletResponse response;

    public HttpServletResponseMDC(ServletResponse response) {
        this.response = (HttpServletResponse) response;
    }

    public static Supplier<DynamicMDC> supplier(ServletResponse response) {
        return () -> new HttpServletResponseMDC(response);
    }

    public static DynamicMDCAdapter.Cleanup put(ServletResponse response) {
        return DynamicMDC.putDynamic("response", supplier(response));
    }

    public static Marker getMarker(HttpServletResponse response) {
        if (response.getStatus() >= 400) {
            return HTTP_ERROR;
        } else if (isRedirect(response.getStatus())) {
            return REDIRECT;
        } else if (response.getStatus() == 304) {
            return NOT_MODIFIED;
        }
        // 300 = MULTIPLE CHOICES
        // 305 = USE PROXY
        // 306 = SWITCH PROXY
        return getMarker(response.getContentType());
    }

    private static Marker getMarker(String contentType) {
        if (contentType == null) {
            return HTTP;
        }
        boolean isAsset = contentType.startsWith("image/") || contentType.startsWith("font/") || contentType.startsWith("text/html") || contentType.startsWith("text/css") || contentType.startsWith("application/javascript");
        boolean isJson = contentType.startsWith("application/json");
        boolean isXml = contentType.startsWith("application/xml");
        if (isAsset) {
            return ASSET;
        } else if (isJson) {
            return JSON;
        } else if (isXml) {
            return XML;
        }
        return HTTP;
    }

    protected static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
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
