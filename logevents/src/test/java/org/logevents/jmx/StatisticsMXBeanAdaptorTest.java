package org.logevents.jmx;

import org.junit.Test;
import org.logevents.observers.StatisticsLogEventsObserver;
import org.slf4j.event.Level;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class StatisticsMXBeanAdaptorTest {

    @Test
    public void shouldCountPerLevel() {
        StatisticsLogEventsObserver observer = new StatisticsLogEventsObserver();
        observer.reset();

        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            observer.addCount(Level.ERROR, now);
        }
        for (int i = 0; i < 8; i++) {
            observer.addCount(Level.WARN, now);
        }
        for (int i = 0; i < 5; i++) {
            observer.addCount(Level.INFO, now);
        }

        StatisticsMXBeanAdaptor mbean = new StatisticsMXBeanAdaptor(Level.WARN);
        assertEquals(8, mbean.getMessagesLast5Minutes());
        assertEquals(8, mbean.getMessagesLastHour());
        assertEquals(8, mbean.getMessagesLastDay());
    }

    @Test
    public void shouldAccumulatePerLevel() {
        StatisticsLogEventsObserver observer = new StatisticsLogEventsObserver();
        observer.reset();

        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            observer.addCount(Level.WARN, now.minusSeconds(60*60*5));
        }
        for (int i = 0; i < 8; i++) {
            observer.addCount(Level.WARN, now.minusSeconds(60*6));
        }
        for (int i = 0; i < 5; i++) {
            observer.addCount(Level.WARN, now.minusSeconds(6));
        }

        StatisticsMXBeanAdaptor mbean = new StatisticsMXBeanAdaptor(Level.WARN);
        assertEquals(5, mbean.getMessagesLast5Minutes());
        assertEquals(13, mbean.getMessagesLastHour());
        assertEquals(23, mbean.getMessagesLastDay());
    }

}