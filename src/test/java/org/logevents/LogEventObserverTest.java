package org.logevents;

import static org.junit.Assert.*;

import org.junit.Test;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class LogEventObserverTest {

    private Logger childLogger = LoggerFactory.getLogger("org.logevents.testing.parent.Child");

    private Logger parentLogger = LoggerFactory.getLogger("org.logevents.testing.parent");

    private Logger grandParentLogger = LoggerFactory.getLogger("org.logevents.testing");

    private LogEventFactory factory = LogEventFactory.getInstance();


    @Test
    public void shouldSendEventToObserver() {
        factory.configure();
        factory.setLevel(Level.WARN);

        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(childLogger, observer, false);

        childLogger.warn("Some Message");
        assertEquals("Some Message", observer.singleMessage());
    }

    @Test
    public void shouldSendEventToParent() {
        factory.configure();
        factory.setLevel(Level.WARN);

        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(parentLogger, observer, false);
        childLogger.warn("Some Message");

        assertEquals("Some Message", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToGrandParent() {
        factory.configure();
        factory.setLevel(Level.WARN);

        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(grandParentLogger, observer, false);
        childLogger.error("Some Message");

        assertEquals("Some Message", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToMultipleObservers() {
        factory.configure();
        factory.setLevel(Level.WARN);

        CircularBufferLogEventObserver parentObserver = new CircularBufferLogEventObserver();
        factory.setObserver(parentLogger, parentObserver, true);

        CircularBufferLogEventObserver childObserver = new CircularBufferLogEventObserver();
        factory.setObserver(childLogger, childObserver, true);

        childLogger.warn("Some Message");

        assertEquals("Some Message", childObserver.singleMessage());
        assertEquals("Some Message", parentObserver.singleMessage());
    }

}
