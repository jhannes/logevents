package org.logeventsdemo;

import org.logevents.LogEventConfigurator;
import org.logevents.LogEventFactory;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

public class DemoLogConfigurator implements LogEventConfigurator {

    @Override
    public void configure(LogEventFactory factory) {
        factory.setLevel(factory.getRootLogger(), Level.DEBUG);
        factory.setRootObserver(new ConsoleLogEventObserver(new DemoLogEventFormatter()));
    }

}
