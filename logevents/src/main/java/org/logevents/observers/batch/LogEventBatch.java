package org.logevents.observers.batch;

import org.logevents.LogEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class LogEventBatch implements Iterable<LogEvent> {
    private List<LogEvent> batch = new ArrayList<>();

    public LogEventBatch(List<LogEvent> batch) {
        this.batch = new ArrayList<>(batch);
    }

    public LogEventBatch() {}

    public LogEventBatch add(LogEvent logEvent) {
        this.batch.add(logEvent);
        return this;
    }

    public Instant firstEventTime() {
        return batch.isEmpty() ? null : batch.get(0).getInstant();
    }

    public boolean isEmpty() {
        return batch.isEmpty();
    }

    public int size() {
        return batch.size();
    }

    @Override
    public Iterator<LogEvent> iterator() {
        return batch.iterator();
    }

    public LogEventGroup firstHighestLevelLogEventGroup() {
        return groups().stream()
                .max((a, b) -> a.headMessage().compareTo(b.headMessage()))
                .orElse(null);
    }

    public boolean isMainGroup(LogEventGroup group) {
        return firstHighestLevelLogEventGroup().isMatching(group.headMessage());
    }

    public Collection<LogEventGroup> groups() {
        List<LogEventGroup> groups = new ArrayList<>();
        for (LogEvent logEvent : batch) {
            if (!groups.isEmpty() && groups.get(groups.size() - 1).isMatching(logEvent)) {
                groups.get(groups.size()-1).add(logEvent);
            } else {
                groups.add(new LogEventGroup(logEvent));
            }
        }
        return groups;
    }

    public Instant latestEventTime() {
        return batch.get(batch.size()-1).getInstant();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEventBatch logEvents = (LogEventBatch) o;
        return Objects.equals(batch, logEvents.batch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batch);
    }

    @Override
    public String toString() {
        return "LogEventBatch{" +
                "size=" + size() +
                ",topLevel=" + (isEmpty() ? "none" : firstHighestLevelLogEventGroup().toString()) +
                '}';
    }

    public void clear() {
        this.batch.clear();
    }
}
