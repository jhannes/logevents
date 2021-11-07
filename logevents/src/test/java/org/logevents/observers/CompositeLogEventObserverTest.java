package org.logevents.observers;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.slf4j.event.Level;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void shouldFilterObserversOnLowerThreshold() {
        CircularBufferLogEventObserver debug = new CircularBufferLogEventObserver();
        LogEventObserver debugObserver = new LevelThresholdConditionalObserver(Level.DEBUG, debug);
        LogEventObserver warnObserver = new LevelThresholdConditionalObserver(Level.WARN, new CircularBufferLogEventObserver());
        LogEventObserver infoObserver = new AbstractFilteredLogEventObserver() {
            {
                setThreshold(Level.INFO);
            }
            @Override
            protected void doLogEvent(LogEvent logEvent) {

            }
        };

        CompositeLogEventObserver combined = (CompositeLogEventObserver) CompositeLogEventObserver.combine(
                debugObserver, warnObserver, infoObserver
        );
        assertEquals(debug, combined.filteredOn(Level.DEBUG, Level.DEBUG));
        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        assertEquals(observer,
                CompositeLogEventObserver.combine(observer, infoObserver).filteredOn(Level.DEBUG, Level.DEBUG));
    }

}
