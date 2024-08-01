package org.logevents.core;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract superclass of LogEventObservers to filter which messages are logged. Supports
 * <code>threshold</code> on {@link Level}, <code>suppressMarkers</code> (don't log messages with any
 * of the given markers), <code>requiredMarker</code> (only log messages with one of the given markers),
 *
 * <h2>Example configuration</h2>
 *
 * <pre>
 * observer.foo.filter=WARN,INFO@marker=INTERESTING
 * observer.foo.suppressMarkers=HTTP_NOT_MODIFIED
 * observer.foo.requireMarker=HTTP
 * observer.foo.requireMdc.user=tester1|tester2
 * observer.foo.suppressMdc.requestPath=/status
 * </pre>
 */
public abstract class AbstractFilteredLogEventObserver implements LogEventObserver {
    private LogEventPredicate condition = new LogEventPredicate.AlwaysCondition();
    private LogEventFilter filter = new LogEventFilter(Level.TRACE);

    @Override
    public final void logEvent(LogEvent logEvent) {
        if (shouldLogEvent(logEvent)) {
            doLogEvent(logEvent);
        }
    }

    protected void configureFilter(Configuration configuration, Level defaultThreshold) {
        this.filter = new LogEventFilter(defaultThreshold);
        configuration.optionalString("threshold")
                .map(LogEventFilter::new)
                .ifPresent(this::setFilter);
        configuration.optionalString("filter")
                .map(LogEventFilter::new)
                .ifPresent(this::setFilter);

        List<LogEventPredicate> allConditions = new ArrayList<>();
        if (!configuration.getStringList("suppressMarkers").isEmpty()) {
            allConditions.add(new LogEventPredicate.SuppressedMarkerCondition(markers(configuration.getStringList("suppressMarkers"))));
        }
        if (!configuration.getStringList("requireMarker").isEmpty()) {
            allConditions.add(new LogEventPredicate.RequiredMarkerCondition(markers(configuration.getStringList("requireMarker"))));
        }

        for (String mdc : configuration.listProperties("requireMdc")) {
            allConditions.add(new LogEventPredicate.RequiredMdcCondition(mdc, configuration.getString("requireMdc." + mdc)));
        }
        for (String mdc : configuration.listProperties("suppressMdc")) {
            allConditions.add(new LogEventPredicate.SuppressedMdcCondition(mdc, configuration.getString("suppressMdc." + mdc)));
        }
        setCondition(LogEventPredicate.allConditions(allConditions));
    }

    private Set<Marker> markers(List<String> markers) {
        return markers.stream().map(MarkerFactory::getMarker).collect(Collectors.toSet());
    }

    protected abstract void doLogEvent(LogEvent logEvent);

    protected boolean shouldLogEvent(LogEvent logEvent) {
        return isEnabled(logEvent.getMarker());
    }

    public void setFilter(LogEventFilter filter) {
        this.filter = filter;
    }

    public void setThreshold(Level threshold) {
        this.filter = new LogEventFilter(threshold);
    }

    public Level getThreshold() {
        return filter.getThreshold();
    }

    @Override
    public LogEventObserver filteredOn(Level level, LogEventPredicate predicate) {
        return LogEventObserver.super.filteredOn(level, filter.getPredicate(level).and(predicate).and(condition));
    }

    @Override
    public boolean isEnabled(Marker marker) {
        return condition.test(marker);
    }

    public LogEventPredicate getCondition() {
        return condition;
    }

    public void setCondition(LogEventPredicate condition) {
        this.condition = condition;
    }
}
