package org.logevents.jmx;

import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.observers.AbstractFilteredLogEventObserver;
import org.logevents.observers.FixedLevelThresholdConditionalObserver;

public class ObserverMXBeanAdaptor implements ObserverMXBean {
    private final LogEventFactory factory;
    private final String observerName;

    public ObserverMXBeanAdaptor(LogEventFactory factory, String observerName) {
        this.factory = factory;
        this.observerName = observerName;
    }

    @Override
    public boolean isCreated() {
        return factory.isObserverCreated(observerName);
    }

    @Override
    public String getContent() {
        return isCreated() ? getObserver().toString() : null;
    }

    @Override
    public String getThreshold() {
        if (!isCreated()) return null;
        LogEventObserver observer = getObserver();
        if (observer instanceof AbstractFilteredLogEventObserver) {
            return ((AbstractFilteredLogEventObserver)observer).getThreshold().toString();
        } else if (observer instanceof FixedLevelThresholdConditionalObserver) {
            return ((FixedLevelThresholdConditionalObserver)observer).getThreshold().toString();
        }
        return null;
    }

    private LogEventObserver getObserver() {
        return factory.getObserver(observerName);
    }
}
