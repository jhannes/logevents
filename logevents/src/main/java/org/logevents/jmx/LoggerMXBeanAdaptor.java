package org.logevents.jmx;

import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.LogEventLogger;
import org.logevents.core.LogEventFilter;
import org.slf4j.event.Level;

import java.util.List;
import java.util.stream.Collectors;

public class LoggerMXBeanAdaptor implements LoggerMXBean {
    private LogEventFactory factory;
    private LogEventLogger logger;

    public LoggerMXBeanAdaptor(LogEventFactory factory, LogEventLogger logger) {
        this.factory = factory;
        this.logger = logger;
    }

    @Override
    public LogEventFilter getFilter() {
        return logger.getOwnFilter();
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
        return stringList(logger.getTraceObservers());
    }

    @Override
    public List<String> getDebugObservers() {
        return stringList(logger.getDebugObservers());
    }

    @Override
    public List<String> getInfoObservers() {
        return stringList(logger.getInfoObservers());
    }

    @Override
    public List<String> getWarnObservers() {
        return stringList(logger.getWarnObservers());
    }

    @Override
    public List<String> getErrorObservers() {
        return stringList(logger.getErrorObservers());
    }

    private List<String> stringList(LogEventObserver observer) {
        return observer.stream().map(Object::toString).collect(Collectors.toList());
    }
}
