package org.logevents.jmx;

import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.slf4j.event.Level;

import java.util.List;

public class LoggerMXBeanAdaptor implements LoggerMXBean {
    private LogEventFactory factory;
    private LoggerConfiguration logger;

    public LoggerMXBeanAdaptor(LogEventFactory factory, LoggerConfiguration logger) {
        this.factory = factory;
        this.logger = logger;
    }

    @Override
    public Level getLevel() {
        return logger.getLevelThreshold();
    }

    @Override
    public void setLevel(Level level) {
        factory.setLevel(logger, level);
    }

    @Override
    public String getObserver() {
        return logger.getObserver();
    }

    @Override
    public List<String> getTraceObservers() {
        return logger.getTraceObservers();
    }

    @Override
    public List<String> getDebugObservers() {
        return logger.getDebugObservers();
    }

    @Override
    public List<String> getInfoObservers() {
        return logger.getInfoObservers();
    }

    @Override
    public List<String> getWarnObservers() {
        return logger.getWarnObservers();
    }

    @Override
    public List<String> getErrorObservers() {
        return logger.getErrorObservers();
    }

}
