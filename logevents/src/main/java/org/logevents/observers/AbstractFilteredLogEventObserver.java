package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Abstract superclass of LogEventObservers to filter which messages are logged. Supports
 * <code>threshold</code> on {@link Level}, <code>suppressMarkers</code> (don't log messages with any
 * of the given markers) and <code>requiredMarker</code> (only log messages with one of the given markers)
 */
public abstract class AbstractFilteredLogEventObserver implements LogEventObserver {
    private Level threshold = Level.TRACE;
    private List<Marker> suppressedMarkers = new ArrayList<>();
    private List<Marker> requireMarker = new ArrayList<>();

    @Override
    public final void logEvent(LogEvent logEvent) {
        if (shouldLogEvent(logEvent)) {
            doLogEvent(logEvent);
        }
    }

    protected void configureFilter(Configuration configuration) {
        this.threshold = configuration.getLevel("threshold", Level.TRACE);
        this.setSuppressMarkerStrings(configuration.getStringList("suppressMarkers"));
        this.setRequireMarkerName(configuration.getStringList("requireMarker"));
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
    public LogEventObserver filteredOn(Level level, Level configuredThreshold) {
        if (configuredThreshold == null || configuredThreshold.compareTo(level) < 0 || getThreshold().toInt() > level.toInt()) {
            return new NullLogEventObserver();
        }
        return this;
    }

    @Override
    public boolean isEnabled(Marker marker) {
        if (marker != null && suppressedMarkers.stream().anyMatch(marker::contains)) {
            return false;
        }
        if (requireMarker.isEmpty()) {
            return true;
        }
        return marker != null && requireMarker.stream().anyMatch(marker::contains);
    }

    public void setSuppressMarkerStrings(List<String> markerNames) {
        setSuppressMarkers(markerNames.stream().map(MarkerFactory::getMarker).collect(Collectors.toList()));
    }

    public void setSuppressMarkers(List<Marker> markers) {
        this.suppressedMarkers = markers;
    }

    public void setRequireMarkerName(List<String> markerNames) {
        setRequireMarker(markerNames.stream().map(MarkerFactory::getMarker).collect(Collectors.toList()));
    }

    public void setRequireMarker(List<Marker> requireMarker) {
        this.requireMarker = requireMarker;
    }

}
