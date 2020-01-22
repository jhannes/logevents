package org.logevents.jmx;

import org.slf4j.event.Level;

import java.util.List;

public interface LogEventFactoryMXBean {

    List<String> getAllLoggers();

    List<String> getConfiguredLoggers();

    List<String> getObservers();

    Level getEffectiveLevel(String logger);

    String getObserver(String logger);

    void reload();
}
