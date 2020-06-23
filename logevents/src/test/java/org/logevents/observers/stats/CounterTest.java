package org.logevents.observers.stats;

import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

public class CounterTest {

    private Instant start = Instant.now().minusSeconds(60*60);

    @Test
    public void shouldForgetOldEvents() {
        Counter trends = new Counter(ChronoUnit.MINUTES, 60);
        trends.addCount(start.minusSeconds(120));

        trends.addCount(start.plusSeconds(120));
        trends.addCount(start.plusSeconds(49*60));
        trends.addCount(start.plusSeconds(49*60));
        trends.addCount(start.plusSeconds(51*60));
        trends.addCount(start.plusSeconds(51*60+1));
        trends.addCount(start.plusSeconds(59*60+2));

        assertEquals(6, trends.getCountSince(start));
        assertEquals(3, trends.getCountSince(start.plusSeconds(50*60)));

        assertEquals(6, trends.getCountSince(start.minusSeconds(180)));
    }

}