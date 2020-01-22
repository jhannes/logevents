package org.logevents.jmx;

import org.slf4j.event.Level;

import java.util.List;

public interface LoggerMXBean {

    Level getLevel();

    void setLevel(Level level);

    String getObserver();

    List<String> getTraceObservers();

    List<String> getDebugObservers();

    List<String> getInfoObservers();

    List<String> getWarnObservers();

    List<String> getErrorObservers();
}
