package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.mdc.DynamicMDC;
import org.logevents.mdc.DynamicMDCAdapter;
import org.logevents.optional.servlets.HttpServletRequestMDC;
import org.logevents.optional.servlets.HttpServletResponseMDC;
import org.logevents.util.JsonUtil;

import java.util.Map;

public class JettyServletMDC implements DynamicMDC {
    private final long duration;

    public static DynamicMDCAdapter.Cleanup put(Request request, Response response) {
        return DynamicMDC.putDynamic("servlet", () -> new JettyServletMDC(request, response));
    }

    private final Request request;
    private final Response response;

    public JettyServletMDC(Request request, Response response) {
        this.request = request;
        this.response = response;
        this.duration = System.currentTimeMillis() - request.getTimeStamp();
    }

    @Override
    public Iterable<? extends Map.Entry<String, String>> entrySet() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        HttpServletResponseMDC.addMdcVariables(result, response);
        HttpServletRequestMDC.addMdcVariables(result, request);
        result.put("http.response.bytes", String.valueOf(response.getHttpChannel().getBytesWritten()));
        result.put("event.time", String.format("%.04f", duration / 1000.0));
        return result.entrySet();
    }

    @Override
    public void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
        HttpServletResponseMDC.populateJson(jsonPayload, response);
        HttpServletRequestMDC.populateJson(jsonPayload, exceptionFormatter, request);
        jsonPayload.put("event.time", String.format("%.04f", duration / 1000.0));

        Map<String, Object> httpResponse = JsonUtil.getObject(JsonUtil.getObject(jsonPayload, "http"), "response");
        httpResponse.put("bytes", response.getHttpChannel().getBytesWritten());
    }

}
