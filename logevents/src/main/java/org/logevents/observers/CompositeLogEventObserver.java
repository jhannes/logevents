package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Delegates log events to a list of observers. Used to deal with configurations
 * what should log events to several destinations. Use {@link #combine(LogEventObserver...)}
 * to easily compose {@link LogEventObserver}s.
 *
 * @author Johannes Brodwall
 */
public class CompositeLogEventObserver implements LogEventObserver {

    private List<LogEventObserver> observers;

    private CompositeLogEventObserver(List<LogEventObserver> observers) {
        this.observers = observers;
    }

    @Override
    public void logEvent(LogEvent event) {
        for (LogEventObserver o : observers) {
            try {
                o.logEvent(event);
            } catch (Exception e) {
                LogEventStatus.getInstance().addError(o, "Failed to log to observer", e);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + observers + "}";
    }

    public static LogEventObserver combine(LogEventObserver... args) {
        return combineList(Arrays.asList(args));
    }

    public static LogEventObserver combineList(Collection<LogEventObserver> args) {
        List<LogEventObserver> observers = new ArrayList<>();
        for (LogEventObserver o : args) {
            if (o instanceof CompositeLogEventObserver) {
                observers.addAll(((CompositeLogEventObserver)o).observers);
            } else if (o != null && !(o instanceof NullLogEventObserver)) {
                observers.add(o);
            }
        }
        if (observers.isEmpty()) {
            return new NullLogEventObserver();
        } else if (observers.size() == 1) {
            return observers.get(0);
        } else {
            return new CompositeLogEventObserver(observers);
        }
    }

    /**
     * Returns the observers which should log messages at the specified level
     */
    public LogEventObserver filteredOn(Level level, Level configuredThreshold) {
        List<LogEventObserver> observers = this.observers.stream()
                .map(o -> o.filteredOn(level, configuredThreshold))
                .filter(o -> !(o instanceof NullLogEventObserver))
                .collect(Collectors.toList());
        return combineList(observers);
    }

    @Override
    public List<LogEventObserver> toList() {
        return observers;
    }
}
