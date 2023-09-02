package org.logevents.optional.jakarta;

import org.logevents.config.MdcFilter;
import org.logevents.formatters.JsonLogEventFormatter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.mdc.DynamicMDC;
import org.logevents.mdc.DynamicMDCAdapter;
import org.logevents.util.JsonUtil;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Populates MDC or JSON format according to <a href="https://www.elastic.co/guide/en/ecs/current/">Elastic
 * Common Schema</a> guidelines:
 *
 * <ul>
 *     <li><strong>url.original:</strong> The URL as the user entered it</li>
 *     <li><strong>http.request.method:</strong> GET, PUT, POST, DELETE etc</li>
 *     <li><strong>user.name:</strong> {@link HttpServletRequest#getRemoteUser()}</li>
 *     <li><strong>client.address:</strong> {@link HttpServletRequest#getRemoteAddr()}</li>
 *     <li><strong>event.time:</strong> The number of seconds since the request started</li>
 *     <li>
 *         <strong>error.{class, message, stack_trace} (only JSON, not MDC)</strong>:
 *          The exception in <code>"javax.servlet.error.exception"</code> (if any)
 *      </li>
 * </ul>
 */
public class HttpServletRequestMDC implements DynamicMDC {

    public static void addMdcVariables(Map<String, String> result, HttpServletRequest request) {
        result.put("http.request.method", request.getMethod());
        result.put("url.original", request.getRequestURL().toString());
        result.put("user.name", request.getRemoteUser());
        result.put("client.address", request.getRemoteHost());
    }

    public static void populateJson(Map<String, Object> jsonPayload, ExceptionFormatter exceptionFormatter, HttpServletRequest request) {
        jsonPayload.put("url.original", request.getRequestURL().toString());
        jsonPayload.put("user.name", request.getRemoteUser());
        jsonPayload.put("client.address", request.getRemoteHost());

        if (jsonPayload.containsKey("http")) {
            JsonUtil.getObject(jsonPayload, "http").put("request.method", request.getMethod());
        } else {
            jsonPayload.put("http.request.method", request.getMethod());
        }

        if (request.getAttribute("javax.servlet.error.exception") instanceof Throwable) {
            Throwable exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
            jsonPayload.put("error", JsonLogEventFormatter.toJsonObject(exception, exceptionFormatter));
        }
    }

    private final HttpServletRequest request;
    private final long duration;

    private HttpServletRequestMDC(ServletRequest request, long duration) {
        this.request = (HttpServletRequest) request;
        this.duration = duration;
    }

    public static Supplier<DynamicMDC> supplier(ServletRequest request) {
        long start = System.currentTimeMillis();
        return () -> new HttpServletRequestMDC(request, System.currentTimeMillis() - start);
    }

    public static DynamicMDCAdapter.Cleanup put(ServletRequest request) {
        return DynamicMDC.putDynamic("request", supplier(request));
    }

    @Override
    public Iterable<? extends Map.Entry<String, String>> entrySet() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        addMdcVariables(result, request);
        result.put("event.time", String.format("%.04f", duration / 1000.0));
        return result.entrySet();
    }

    @Override
    public void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
        populateJson(jsonPayload, exceptionFormatter, request);
        jsonPayload.put("event.time", String.format("%.04f", duration / 1000.0));
    }
}
