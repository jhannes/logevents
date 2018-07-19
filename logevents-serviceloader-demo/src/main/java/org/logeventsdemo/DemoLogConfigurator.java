package org.logeventsdemo;

import org.logevents.LogEventConfigurator;
import org.logevents.LogEventFactory;
import org.logevents.destinations.ConsoleLogEventDestination;
import org.logevents.observers.TextLogEventObserver;
import org.slf4j.event.Level;

public class DemoLogConfigurator implements LogEventConfigurator {

    @Override
    public void configure(LogEventFactory factory) {
        factory.setLevel(factory.getRootLogger(), Level.DEBUG);
        factory.setObserver(factory.getRootLogger(),
                new TextLogEventObserver(new ConsoleLogEventDestination(), new DemoLogEventFormatter()),
                true);
    }

}
