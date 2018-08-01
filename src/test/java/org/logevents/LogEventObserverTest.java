package org.logevents;

import static org.junit.Assert.*;

import org.junit.Test;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.observers.NullLogEventObserver;
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
        factory.setRootLevel(Level.WARN);

        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(childLogger, observer, false);

        childLogger.warn("Message sent to observer");
        assertEquals("Message sent to observer", observer.singleMessage());
    }

    @Test
    public void shouldSendEventToParent() {
        factory.configure();
        factory.setRootLevel(Level.WARN);

        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(parentLogger, observer, false);
        childLogger.warn("Message sent to parent");

        assertEquals("Message sent to parent", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToGrandParent() {
        factory.configure();
        factory.setRootLevel(Level.WARN);

        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(grandParentLogger, observer, false);
        childLogger.error("Message sent to grandparent");

        assertEquals("Message sent to grandparent", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToMultipleObservers() {
        factory.configure();
        factory.setRootLevel(Level.WARN);
        factory.setRootObserver(new NullLogEventObserver());

        CircularBufferLogEventObserver parentObserver = new CircularBufferLogEventObserver();
        factory.setObserver(parentLogger, parentObserver, true);

        CircularBufferLogEventObserver childObserver = new CircularBufferLogEventObserver();
        factory.setObserver(childLogger, childObserver, true);
        CircularBufferLogEventObserver childSecondaryObserver = new CircularBufferLogEventObserver();
        factory.addObserver(childLogger, childSecondaryObserver);

        childLogger.warn("Message sent to multiple");

        assertEquals("Message sent to multiple", childObserver.singleMessage());
        assertEquals("Message sent to multiple", childSecondaryObserver.singleMessage());
        assertEquals("Message sent to multiple", parentObserver.singleMessage());
    }

}
