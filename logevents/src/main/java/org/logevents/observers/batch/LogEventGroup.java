package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LogEventGroup {

    private List<LogEvent> logEvents = new ArrayList<>();

    public LogEventGroup(LogEvent logEvent) {
        logEvents.add(logEvent);
    }

    public LogEventGroup(List<LogEvent> events) {
        logEvents.addAll(events);
    }

    public void add(LogEvent logEvent) {
        this.logEvents.add(logEvent);
    }

    public boolean isMatching(LogEvent logEvent) {
        return headMessage().getLevel().equals(logEvent.getLevel())
                && headMessage().getLoggerName().equals(logEvent.getLoggerName())
                && headMessage().getMessage().equals(logEvent.getMessage());
    }

    public LogEvent headMessage() {
        return logEvents.get(0);
    }

    public Instant latestEventTime() {
        return logEvents.get(logEvents.size()-1).getInstant();
    }

    public Instant firstEventTime() {
        return logEvents.get(0).getInstant();
    }

    public int size() {
        return logEvents.size();
    }

    public String getMessage() {
        return headMessage().getMessage();
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" + size() + " x '" + headMessage() + "'}";
    }

    public Level getLevel() {
        return headMessage().getLevel();
    }
}
