package org.logevents.observers.batch;

import org.logevents.LogEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LogEventBatch {
    private List<LogEvent> batch = new ArrayList<>();

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
}
