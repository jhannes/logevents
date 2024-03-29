package org.logevents.jmx;

import org.logevents.core.LogEventFilter;

import java.util.List;

public interface LogEventFactoryMXBean {

    List<String> getAllLoggers();

    List<String> getConfiguredLoggers();

    List<String> getObservers();

    LogEventFilter getEffectiveFilter(String logger);

    String getObserver(String logger);

    void reload();
}
