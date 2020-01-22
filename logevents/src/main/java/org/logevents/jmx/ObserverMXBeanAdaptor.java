package org.logevents.jmx;

import org.logevents.LogEventFactory;

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
}
