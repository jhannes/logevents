package org.logevents.observers;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEventObserver;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;

import static org.junit.Assert.*;

public class CompositeLogEventObserverTest {

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule();

    @Test
    public void shouldCombineNullObserverIntoNullObserver() {
        NullLogEventObserver o = new NullLogEventObserver();
        assertEquals(o, CompositeLogEventObserver.combine(o));
    }

    @Test
    public void shouldCombineSingletons() {
        System.out.println();
        NullLogEventObserver nullObserver = new NullLogEventObserver();
        LogEventObserver o = new CircularBufferLogEventObserver();
        assertEquals(o, CompositeLogEventObserver.combine(o));
        assertEquals(o, CompositeLogEventObserver.combine(nullObserver, o));
        assertEquals(o, CompositeLogEventObserver.combine(o, nullObserver));
    }

    @Test
    public void shouldCatchExceptionInObserver() {
        RuntimeException exception = new RuntimeException("Failed to process event!");

        CircularBufferLogEventObserver buffer = new CircularBufferLogEventObserver();
        LogEventObserver badObserver = logEvent -> {
            throw exception;
        };
        LogEventObserver observer = CompositeLogEventObserver.combine(badObserver, buffer);
        logEventStatusRule.setStatusLevel(StatusEvent.StatusLevel.FATAL);
        observer.logEvent(new LogEventSampler().build());
        assertEquals(exception, LogEventStatus.getInstance().lastMessage().getThrowable());
    }

}
