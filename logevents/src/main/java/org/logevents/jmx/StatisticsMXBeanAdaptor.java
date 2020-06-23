package org.logevents.jmx;

import org.logevents.observers.StatisticsLogEventsObserver;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;

public class StatisticsMXBeanAdaptor implements StatisticsMXBean {

    private final Level level;

    public StatisticsMXBeanAdaptor(Level level) {
        this.level = level;
    }

    @Override
    public int getMessagesLast5Minutes() {
        return getCount(Duration.ofMinutes(5));
    }

    @Override
    public int getMessagesLastHour() {
        return getCount(Duration.ofMinutes(60));
    }

    @Override
    public int getMessagesLastDay() {
        return getHourlyCount(Duration.ofHours(24));
    }

    public int getCount(Duration since) {
        return StatisticsLogEventsObserver.getCountSince(level, Instant.now().minus(since));
    }

    public int getHourlyCount(Duration since) {
        return StatisticsLogEventsObserver.getHourlyCountSince(level, Instant.now().minus(since));
    }

}
