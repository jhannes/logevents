package org.slf4j.impl;

import org.logevents.LogEventFactory;
import org.slf4j.spi.LoggerFactoryBinder;

public class StaticLoggerBinder implements LoggerFactoryBinder {

    private static StaticLoggerBinder instance = new StaticLoggerBinder();

    public static StaticLoggerBinder getSingleton() {
        return instance;
    }

    @Override
    public LogEventFactory getLoggerFactory() {
        return LogEventFactory.getInstance();
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return LogEventFactory.class.getName();
    }

}
