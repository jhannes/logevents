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
import java.util.stream.Stream;

public abstract class FilteredLogEventObserver implements LogEventObserver {
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
        this.threshold = configuration.optionalString("threshold").map(Level::valueOf).orElse(Level.TRACE);
        setSuppressMarkers(configuration.getStringList("suppressMarkers"));
        setRequireMarker(configuration.getStringList("requireMarker"));
    }


    protected abstract void doLogEvent(LogEvent logEvent);

    protected boolean shouldLogEvent(LogEvent logEvent) {
        if (logEvent.getLevel().compareTo(threshold) > 0) return false;
        for (Marker suppressedMarker : suppressedMarkers) {
            if (logEvent.getMarker() != null && suppressedMarker.contains(logEvent.getMarker())) {
                return false;
            }
        }
        if (!requireMarker.isEmpty()) {
            for (Marker requireMarker : requireMarker) {
                if (logEvent.getMarker() != null && requireMarker.contains(logEvent.getMarker())) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    public void setThreshold(Level threshold) {
        this.threshold = threshold;
    }

    public Level getThreshold() {
        return threshold;
    }

    public void setSuppressMarkers(String[] markerNames) {
        setSuppressMarkers(Stream.of(markerNames).map(MarkerFactory::getMarker).collect(Collectors.toList()));
    }

    public void setSuppressMarkers(List<Marker> markers) {
        this.suppressedMarkers = markers;
    }

    public void setRequireMarker(String[] markerNames) {
        setRequireMarker(Stream.of(markerNames).map(MarkerFactory::getMarker).collect(Collectors.toList()));
    }

    private void setRequireMarker(List<Marker> requireMarker) {
        this.requireMarker = requireMarker;
    }

}
