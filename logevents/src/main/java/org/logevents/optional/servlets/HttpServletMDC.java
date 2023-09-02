package org.logevents.optional.servlets;

import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.mdc.DynamicMDC;
import org.logevents.mdc.DynamicMDCAdapter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
 *     </li>
 *     <li><strong>http.response.status_code</strong>: {@link HttpServletResponse#getStatus()}</li>
 *     <li><strong>http.response.mime_type</strong>: {@link HttpServletResponse#getContentType()}</li>
 *     <li><strong>http.response.redirect</strong>: "Location" response header</li>
 * </ul>
 */
public class HttpServletMDC implements DynamicMDC {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final long duration;

    public HttpServletMDC(ServletRequest request, ServletResponse response, long duration) {
        this.request = (HttpServletRequest) request;
        this.response = (HttpServletResponse) response;
        this.duration = duration;
    }

    public static Supplier<DynamicMDC> supplier(ServletRequest request, ServletResponse response) {
        long start = System.currentTimeMillis();
        return () -> new HttpServletMDC(request, response, System.currentTimeMillis() - start);
    }

    public static DynamicMDCAdapter.Cleanup put(ServletRequest request, ServletResponse response) {
        return DynamicMDC.putDynamic("servlet", supplier(request, response));
    }

    @Override
    public Iterable<? extends Map.Entry<String, String>> entrySet() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        HttpServletResponseMDC.addMdcVariables(result, response);
        HttpServletRequestMDC.addMdcVariables(result, request);
        result.put("event.time", String.format("%.04f", duration / 1000.0));
        return result.entrySet();
    }

    @Override
    public void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
        HttpServletResponseMDC.populateJson(jsonPayload, response);
        HttpServletRequestMDC.populateJson(jsonPayload, exceptionFormatter, request);
        jsonPayload.put("event.time", String.format("%.04f", duration / 1000.0));
    }
}
