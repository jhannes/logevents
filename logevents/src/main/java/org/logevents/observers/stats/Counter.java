package org.logevents.observers.stats;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Calculates usage of some call with a resolution (MINUTES, HOURS, DAYS etc) with a retention.
 * For example, resolution=MINUTE and retention=60 keeps statistics for the last hour with a
 * resolution of 1 minutes. Frees historical data beyond the retention window, ensuring
 * constant bounded memory usage.
 */
public class Counter {

    private static class StatisticsInterval {
        private Instant start;
        private int count;

        StatisticsInterval(Instant start, int count) {
            this.start = start;
            this.count = count;
        }
    }

    private final ChronoUnit resolution;
    private final int retention;
    private LinkedList<StatisticsInterval> intervals = new LinkedList<>();

    public Counter(ChronoUnit resolution, int retention) {
        this.resolution = resolution;
        this.retention = retention;
    }

    public synchronized void addCount(Instant now) {
        Instant timeBucket = now.truncatedTo(resolution);

        if (intervals.isEmpty()) {
            intervals.add(new StatisticsInterval(timeBucket, 1));
        } else if (intervals.getLast().start.isAfter(timeBucket)) {
            throw new IllegalArgumentException("Events must be increasing in time");
        } else if (intervals.getLast().start.equals(timeBucket)) {
            intervals.getLast().count++;
        } else {
            intervals.add(new StatisticsInterval(timeBucket, 1));
        }

        Instant rententionWindowStart = timeBucket.minus(resolution.getDuration().multipliedBy(retention));
        intervals.removeIf(next -> next.start.isBefore(rententionWindowStart));
    }

    public synchronized int getCountSince(Instant start) {
        int count = 0;
        start = start.truncatedTo(resolution);
        Iterator<StatisticsInterval> it = intervals.descendingIterator();
        while (it.hasNext()) {
            StatisticsInterval next =  it.next();
            if (next.start.isBefore(start)) {
                break;
            }
            count += next.count;
        }
        return count;
    }
}
