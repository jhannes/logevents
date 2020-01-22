package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;

public class TestObserver implements LogEventObserver {
    private final String name;

    public TestObserver(String name) {
        this.name = name;
    }

    @Override
    public void logEvent(LogEvent logEvent) {

    }

    @Override
    public String toString() {
        return "TestObserver{" + name + '}';
    }
}
