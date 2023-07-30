package org.logevents.mdc;

import org.logevents.config.MdcFilter;
import org.logevents.formatters.JsonLogEventFormatter;
import org.logevents.formatters.exceptions.ExceptionFormatter;

import java.util.HashMap;
import java.util.Map;

public class ExceptionMDC implements DynamicMDC {
    private final Throwable exception;

    public ExceptionMDC(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public Iterable<? extends Map.Entry<String, String>> entrySet() {
        HashMap<String, String> entries = new HashMap<>();
        entries.put("exception", exception.toString());
        return entries.entrySet();
    }

    @Override
    public void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
        jsonPayload.put("error", JsonLogEventFormatter.toJsonObject(exception, exceptionFormatter));
    }
}
