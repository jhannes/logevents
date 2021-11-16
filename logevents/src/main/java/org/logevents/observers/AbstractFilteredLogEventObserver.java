package org.logevents.observers;

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
 * observer.foo.threshold=WARN
 * observer.foo.suppressMarkers=HTTP_NOT_MODIFIED
 * observer.foo.requireMarker=HTTP
 * observer.foo.requireMdc.user=tester1|tester2
 * observer.foo.suppressMdc.requestPath=/status
 * </pre>
 */
public abstract class AbstractFilteredLogEventObserver implements LogEventObserver {
    private Level threshold = Level.TRACE;
    private LogEventPredicate condition = new LogEventPredicate.AlwaysCondition();

    @Override
    public final void logEvent(LogEvent logEvent) {
        if (shouldLogEvent(logEvent)) {
            doLogEvent(logEvent);
        }
    }

    protected void configureFilter(Configuration configuration, Level defaultThreshold) {
        this.threshold = configuration.optionalString("threshold").map(Level::valueOf).orElse(defaultThreshold);

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
        return !logEvent.isBelowThreshold(threshold) && isEnabled(logEvent.getMarker());
    }

    public void setThreshold(Level threshold) {
        this.threshold = threshold;
    }

    public Level getThreshold() {
        return threshold;
    }

    @Override
    public LogEventObserver filteredOn(Level level, boolean enabledByFilter) {
        return enabledByFilter && getThreshold().toInt() <= level.toInt() ? this : new NullLogEventObserver();
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
