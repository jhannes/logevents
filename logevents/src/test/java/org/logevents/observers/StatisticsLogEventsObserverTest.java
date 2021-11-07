package org.logevents.observers;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StatisticsLogEventsObserverTest {
    private final StatisticsLogEventsObserver observer = new StatisticsLogEventsObserver();
    private final Instant start = Instant.now().minusSeconds(60*60);

    @Before
    public void resetStats() {
        observer.reset();
    }

    @Test
    public void shouldAccumulateEventsPerLevel() {
        observer.addCount(Level.INFO, start.plusSeconds(60));
        observer.addCount(Level.INFO, start.plusSeconds(10*60));
        observer.addCount(Level.INFO, start.plusSeconds(30*60));
        observer.addCount(Level.ERROR, start.plusSeconds(30*60));
        observer.addCount(Level.INFO, start.plusSeconds(50*60));

        assertEquals(4, StatisticsLogEventsObserver.getCountSince(Level.INFO, start));
        assertEquals(1, StatisticsLogEventsObserver.getCountSince(Level.ERROR, start));
        assertEquals(0, StatisticsLogEventsObserver.getCountSince(Level.WARN, start));
    }

    @Test
    public void shouldUseSpecifiedResolution() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(2);

        observer.addCount(Level.INFO, now.minusSeconds(125));
        observer.addCount(Level.INFO, now.minusSeconds(110));
        observer.addCount(Level.INFO, now.minusSeconds(100));
        observer.addCount(Level.INFO, now.minusSeconds(90));

        assertEquals(4, StatisticsLogEventsObserver.getCountSince(Level.INFO, now.minusSeconds(180)));
        assertEquals(3, StatisticsLogEventsObserver.getCountSince(Level.INFO, now.minusSeconds(120)));
        assertEquals("resolution is too high to distinguish between 110, 100 and 90 seconds ago",
                3,
                StatisticsLogEventsObserver.getCountSince(Level.INFO, now.minusSeconds(100)));
    }

    @Test
    public void shouldShowMostRecentTime() {
        Instant errorTime = start.plusSeconds(30 * 10);
        Instant infoTime = start.plusSeconds(50 * 20);
        observer.addCount(Level.ERROR, errorTime);
        observer.addCount(Level.INFO, infoTime);

        assertEquals(errorTime, StatisticsLogEventsObserver.getLastMessageTime(Level.ERROR));
        assertEquals(infoTime, StatisticsLogEventsObserver.getLastMessageTime(Level.INFO));
        assertNull(StatisticsLogEventsObserver.getLastMessageTime(Level.WARN));

        observer.addCount(Level.ERROR, errorTime.plusSeconds(10));
        assertEquals(errorTime.plusSeconds(10), StatisticsLogEventsObserver.getLastMessageTime(Level.ERROR));
    }

    @Test
    public void shouldAccumulatePerDay() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS).plusSeconds(122);

        observer.addCount(Level.INFO, now.minusSeconds(60*60*26)); // scrolled out of window
        observer.addCount(Level.INFO, now.minusSeconds(60*60*23)); // Included in last 24 hours
        observer.addCount(Level.INFO, now.minusSeconds(60*60*3)); // Included in last 3 hours
        observer.addCount(Level.INFO, now.minusSeconds(60*60*3-10*60));
        observer.addCount(Level.INFO, now.minusSeconds(60*60*3-20*60)); // Can't distinguish between 3,5 and 3 hours ago
        observer.addCount(Level.INFO, now.minusSeconds(30*60));


        assertEquals(5, StatisticsLogEventsObserver.getHourlyCountSince(Level.INFO, now.minusSeconds(60*60*24)));
        assertEquals(4, StatisticsLogEventsObserver.getHourlyCountSince(Level.INFO, now.minusSeconds(60*60*4)));
        assertEquals("resolution is too high to distinguish between 3H, 2H50M, 2H40M and 2H30 seconds ago",
                4, StatisticsLogEventsObserver.getHourlyCountSince(Level.INFO, now.minusSeconds(60*60*3-20*60)));

        assertEquals("should expire stats after retention",
                5, StatisticsLogEventsObserver.getHourlyCountSince(Level.INFO, now.minusSeconds(60*60*24*2)));
    }


}
