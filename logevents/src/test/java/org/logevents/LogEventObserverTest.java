package org.logevents;

import org.junit.Test;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.core.NullLogEventObserver;
import org.slf4j.Logger;

import static org.junit.Assert.assertEquals;

public class LogEventObserverTest {

    private final LogEventFactory factory = new LogEventFactory();

    private final Logger childLogger = factory.getLogger("org.logevents.testing.parent.Child");

    private final Logger parentLogger = factory.getLogger("org.logevents.testing.parent");

    private final Logger grandParentLogger = factory.getLogger("org.logevents.testing");

    @Test
    public void shouldSendEventToObserver() {
        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(childLogger, observer, false);

        childLogger.warn("Message sent to observer");
        assertEquals("Message sent to observer", observer.singleMessage());
    }

    @Test
    public void shouldSendEventToParent() {
        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(parentLogger, observer, false);
        childLogger.warn("Message sent to parent");

        assertEquals("Message sent to parent", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToGrandParent() {
        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver(grandParentLogger, observer, false);
        childLogger.error("Message sent to grandparent");

        assertEquals("Message sent to grandparent", observer.singleMessage());
    }


    @Test
    public void shouldSendEventToMultipleObservers() {
        factory.setRootObserver(new NullLogEventObserver());

        CircularBufferLogEventObserver parentObserver = new CircularBufferLogEventObserver();
        factory.setObserver(parentLogger, parentObserver, true);

        CircularBufferLogEventObserver childObserver = new CircularBufferLogEventObserver();
        factory.setObserver(childLogger, childObserver, true);
        CircularBufferLogEventObserver childSecondaryObserver = new CircularBufferLogEventObserver();
        factory.addObserver("org.logevents.testing.parent.Child", childSecondaryObserver);

        childLogger.warn("Message sent to multiple");

        assertEquals("Message sent to multiple", childObserver.singleMessage());
        assertEquals("Message sent to multiple", childSecondaryObserver.singleMessage());
        assertEquals("Message sent to multiple", parentObserver.singleMessage());
    }

}
