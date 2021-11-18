package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Delegates log events to a list of observers. Used to deal with configurations
 * what should log events to several destinations. Use {@link #combine(LogEventObserver...)}
 * to easily compose {@link LogEventObserver}s.
 *
 * @author Johannes Brodwall
 */
public class CompositeLogEventObserver implements LogEventObserver {

    private Collection<LogEventObserver> observers;

    private CompositeLogEventObserver(Collection<LogEventObserver> observers) {
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
        Collection<LogEventObserver> observers = new LinkedHashSet<>();
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
            return observers.iterator().next();
        } else {
            return new CompositeLogEventObserver(observers);
        }
    }

    /**
     * Returns the observers which should log messages at the specified level
     */
    @Override
    public LogEventObserver filteredOn(Level level, LogEventPredicate predicate) {
        List<LogEventObserver> observers = this.observers.stream()
                .map(o -> o.filteredOn(level, predicate))
                .filter(o -> !(o instanceof NullLogEventObserver))
                .collect(Collectors.toList());
        return combineList(observers);
    }

    @Override
    public Stream<LogEventObserver> stream() {
        return observers.stream();
    }
}
