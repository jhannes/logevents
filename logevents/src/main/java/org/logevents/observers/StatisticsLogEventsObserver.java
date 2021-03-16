package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.util.Counter;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Counts logevents per minute and hour grouped by Level. Can be used as source for statistics
 * for JMX or other statistics. Usage
 *
 * <pre>
 * observer.stats=StatisticsLogEventsObserver
 * observer.stats.suppressMarkers=UNINTERESTING
 * root.observer.stats=DEBUG
 *
 * logevents.jmx=true
 * </pre>
 */
public class StatisticsLogEventsObserver extends AbstractFilteredLogEventObserver {

    private static final Map<Level, Counter> logEventsPerHour = Collections.synchronizedMap(new EnumMap<>(Level.class));
    private static final Map<Level, Counter> logEventsPerMinute = Collections.synchronizedMap(new EnumMap<>(Level.class));
    private static final Map<Level, Instant> lastEvent = Collections.synchronizedMap(new EnumMap<>(Level.class));

    @Override
    protected void doLogEvent(LogEvent logEvent) {
        addCount(logEvent.getLevel(), logEvent.getInstant());
    }

    public void addCount(Level level, Instant now) {
        getRequestPerMinute(level).addCount(now);
        getRequestPerHour(level).addCount(now);
        lastEvent.put(level, now);
    }

    public void reset() {
        logEventsPerHour.clear();
        logEventsPerMinute.clear();
        lastEvent.clear();
    }

    private static Counter getRequestPerHour(Level level) {
        return logEventsPerHour.computeIfAbsent(level, l -> new Counter(ChronoUnit.HOURS, 24));
    }

    private static Counter getRequestPerMinute(Level level) {
        return logEventsPerMinute.computeIfAbsent(level, l -> new Counter(ChronoUnit.MINUTES, 60));
    }

    public static int getCountSince(Level level, Instant since) {
        return getRequestPerMinute(level).getCountSince(since);
    }

    public static int getHourlyCountSince(Level level, Instant since) {
        return getRequestPerHour(level).getCountSince(since);
    }

    public static Instant getLastMessageTime(Level level) {
        return lastEvent.get(level);
    }
}
