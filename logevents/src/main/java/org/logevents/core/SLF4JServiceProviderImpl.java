package org.logevents.core;

import org.logevents.LogEventFactory;
import org.logevents.mdc.DynamicMDCAdapterImplementation;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public class SLF4JServiceProviderImpl implements SLF4JServiceProvider {

    // to avoid constant folding by the compiler, this field must *not* be final
    public static String REQUESTED_API_VERSION = "1.8.99"; // !final

    private final ILoggerFactory loggerFactory = LogEventFactory.getInstance();
    private final IMarkerFactory markerFactory =  new BasicMarkerFactory();
    private final MDCAdapter mdcAdapter =  new DynamicMDCAdapterImplementation();

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0";
    }

    // Preserved for compatibility with slf4j-api 1.8
    public String getRequesteApiVersion() {
        return REQUESTED_API_VERSION;
    }

    @Override
    public void initialize() {

    }
}
