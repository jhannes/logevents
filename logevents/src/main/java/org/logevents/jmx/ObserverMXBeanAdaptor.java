package org.logevents.jmx;

import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.observers.FilteredLogEventObserver;
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
        return isCreated() ? factory.getObserver(observerName).toString() : null;
    }

    @Override
    public String getThreshold() {
        if (!isCreated()) return null;
        LogEventObserver observer = factory.getObserver(observerName);
        if (observer instanceof FilteredLogEventObserver) {
            return ((FilteredLogEventObserver)observer).getThreshold().toString();
        } else if (observer instanceof FixedLevelThresholdConditionalObserver) {
            return ((FixedLevelThresholdConditionalObserver)observer).getThreshold().toString();
        }
        return null;
    }
}
