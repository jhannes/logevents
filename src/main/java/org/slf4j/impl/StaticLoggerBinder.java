package org.slf4j.impl;

import org.logevents.LogEventFactory;
import org.slf4j.spi.LoggerFactoryBinder;

public class StaticLoggerBinder implements LoggerFactoryBinder {

    private static StaticLoggerBinder instance = new StaticLoggerBinder();

    public static StaticLoggerBinder getSingleton() {
        return instance;
    }

    private LogEventFactory loggerFactory = new LogEventFactory();

    @Override
    public LogEventFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return loggerFactory.getClass().getName();
    }

}
