package org.logevents.optional.servlets;

import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.mdc.DynamicMDC;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Supplier;

public class HttpServletRequestMDC implements DynamicMDC {
    private final HttpServletRequest request;
    private final long duration;

    private HttpServletRequestMDC(HttpServletRequest request, long duration) {
        this.request = request;
        this.duration = duration;
    }

    public static Supplier<DynamicMDC> supplier(HttpServletRequest request) {
        long start = System.currentTimeMillis();
        return () -> new HttpServletRequestMDC(request, System.currentTimeMillis() - start);
    }

    @Override
    public Iterable<? extends Map.Entry<String, String>> entrySet() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("http.request.method", request.getMethod());
        result.put("url.original", request.getRequestURL().toString());
        result.put("user.name", request.getRemoteUser());
        result.put("client.address", request.getRemoteHost());
        result.put("event.time", String.format("%.04f", duration / 1000.0));
        return result.entrySet();
    }

    @Override
    public void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
        jsonPayload.put("url.original", request.getRequestURL().toString());
        jsonPayload.put("user.name", request.getRemoteUser());
        jsonPayload.put("client.address", request.getRemoteHost());
        jsonPayload.put("event.time", String.format("%.04f", duration / 1000.0));
        jsonPayload.put("http.request.method", request.getMethod());
    }
}
