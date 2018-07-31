package org.logevents.observers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.observers.batch.LogEventGroup;
import org.slf4j.event.Level;

public class BatchingLogEventObserverTest {

    @Test
    public void shouldAccumulateSimilarMessages() {
        BatchingLogEventObserver observer = new BatchingLogEventObserver(null);

        String messageFormat = "This is a message about {}";
        observer.addToBatch(new LogEvent(getClass().getName(), Level.INFO, null, messageFormat, new Object[] { "cheese" }));
        observer.addToBatch(new LogEvent(getClass().getName(), Level.INFO, null, messageFormat, new Object[] { "ham" }));

        List<LogEventGroup> batch = observer.takeCurrentBatch();
        assertEquals(1, batch.size());
        assertEquals(messageFormat, batch.get(0).headMessage().getMessage());
        assertArrayEquals(new Object[] { "cheese" }, batch.get(0).headMessage().getArgumentArray());
        assertEquals(2, batch.get(0).size());

        assertEquals(0, observer.takeCurrentBatch().size());
    }

}
