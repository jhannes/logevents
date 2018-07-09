package org.logevents;

import static org.junit.Assert.*;

import org.junit.Test;
import org.logevents.observers.CyclicBufferLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class LogEventObserverTest {
    private LogEventConfigurator configurator = new LogEventConfigurator();

    private Logger childLogger = LoggerFactory.getLogger("org.logevents.testing.parent.Child");

    private Logger parentLogger = LoggerFactory.getLogger("org.logevents.testing.parent");

    private Logger grandParentLogger = LoggerFactory.getLogger("org.logevents.testing");


    @Test
    public void shouldSendEventToObserver() {
        configurator.reset();
        configurator.setLevel(Level.WARN);

        CyclicBufferLogEventObserver observer = new CyclicBufferLogEventObserver();
        configurator.setObserver(childLogger, observer, false);

        childLogger.warn("Some Message");
        assertEquals("Some Message", observer.singleMessage());
    }

    @Test
    public void shouldSendEventToParent() {
        configurator.reset();
        configurator.setLevel(Level.WARN);

        CyclicBufferLogEventObserver observer = new CyclicBufferLogEventObserver();
        configurator.setObserver(parentLogger, observer, false);
        childLogger.warn("Some Message");

        assertEquals("Some Message", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToGrandParent() {
        configurator.reset();
        configurator.setLevel(Level.WARN);

        CyclicBufferLogEventObserver observer = new CyclicBufferLogEventObserver();
        configurator.setObserver(grandParentLogger, observer, false);
        childLogger.error("Some Message");

        assertEquals("Some Message", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToMultipleObservers() {
        configurator.reset();
        configurator.setLevel(Level.WARN);

        CyclicBufferLogEventObserver parentObserver = new CyclicBufferLogEventObserver();
        configurator.setObserver(parentLogger, parentObserver, true);

        CyclicBufferLogEventObserver childObserver = new CyclicBufferLogEventObserver();
        configurator.setObserver(childLogger, childObserver, true);

        childLogger.warn("Some Message");

        assertEquals("Some Message", childObserver.singleMessage());
        assertEquals("Some Message", parentObserver.singleMessage());
    }

}
