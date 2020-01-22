package org.logevents.jmx;

import org.logevents.LogEventFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LogEventFactoryMXBeanAdaptor implements LogEventFactoryMXBean {
    private LogEventFactory factory;

    public LogEventFactoryMXBeanAdaptor(LogEventFactory factory) {
        this.factory = factory;
    }

    @Override
    public List<String> getConfiguredLoggers() {
        return factory.getLoggers().entrySet().stream()
                .filter(entry -> entry.getValue().isConfigured())
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getObservers() {
        return new ArrayList<>(factory.getObserverNames());
    }

    @Override
    public List<String> getAllLoggers() {
        return factory.getLoggers().keySet().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public Level getEffectiveLevel(String logger) {
        return factory.getLogger(logger).getEffectiveThreshold();
    }

    @Override
    public String getObserver(String logger) {
        return factory.getLogger(logger).getObserver();
    }

    @Override
    public void reload() {
        factory.configure();
    }
}
