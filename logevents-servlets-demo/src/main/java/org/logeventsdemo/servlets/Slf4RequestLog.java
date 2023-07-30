package org.logeventsdemo.servlets;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.logevents.mdc.DynamicMDCAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

public class Slf4RequestLog implements RequestLog {

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

    private static final Logger log = LoggerFactory.getLogger(Slf4RequestLog.class);
    @Override
    public final void log(Request request, Response response) {
        try (DynamicMDCAdapter.Cleanup ignored = JettyServletMDC.put(request, response)) {
            doLog(response);
        }
    }

    protected void doLog(Response response) {
        log.atLevel(getLevel(response)).addMarker(getMarker(response)).log("Request processed");
    }

    protected Marker getMarker(Response response) {
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

    protected static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    protected Marker getMarker(String contentType) {
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

    protected Level getLevel(Response response) {
        if (response.getStatus() >= 500) {
            return Level.WARN;
        } else if (response.getStatus() >= 400 || isRedirect(response.getStatus())) {
            return Level.INFO;
        } else if (response.getStatus() < 100) {
            return Level.ERROR;
        } else {
            return Level.DEBUG;
        }
    }
}
