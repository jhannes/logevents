package org.logevents.query;

import org.logevents.LogEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class LogEventSummary {
    private Set<String> threads = new TreeSet<>();
    private Set<String> markers = new TreeSet<>();
    private Set<String> loggers = new TreeSet<>();
    private Set<String> nodes = new TreeSet<>();
    private Set<String> applications = new TreeSet<>();
    private Map<String, Set<String>> mdcMap = new TreeMap<>();
    private int rowCount;
    private int filteredCount;

    public Map<String, Object> toJson() {
        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("rowCount", rowCount);
        facets.put("filteredCount", filteredCount);
        facets.put("threads", threads);
        facets.put("loggers", getLoggerSummary());
        facets.put("markers", markers);
        facets.put("applications", applications);
        facets.put("nodes", nodes);
        facets.put("mdc", calculateMdc());
        return facets;
    }

    public List<Map<String, String>> getLoggerSummary() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String loggerName : loggers) {
            Map<String, String> logger = new HashMap<>();
            logger.put("name", loggerName);
            logger.put("abbreviatedName", LogEvent.getAbbreviatedLoggerName(loggerName, 0));
            result.add(logger);
        }
        return result;
    }

    private Object calculateMdc() {
        List<Map<String, Object>> mdcSummary = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : mdcMap.entrySet()) {
            Map<String, Object> mdcEntry = new LinkedHashMap<>();
            mdcEntry.put("name", entry.getKey());
            mdcEntry.put("values", entry.getValue().stream().sorted().collect(Collectors.toList()));
            mdcSummary.add(mdcEntry);
        }
        mdcSummary.sort(Comparator.comparing(m -> m.get("name").toString()));
        return mdcSummary;
    }

    public void add(LogEvent event) {
        this.rowCount++;
        this.loggers.add(event.getLoggerName());
        this.threads.add(event.getThreadName());
        Map<String, String> mdc = event.getMdcProperties();
        for (String mdcKey : mdc.keySet()) {
            if (mdc.get(mdcKey) != null) {
                mdcMap.computeIfAbsent(mdcKey, k -> new TreeSet<>()).add(mdc.get(mdcKey));
            }
        }
        if (event.getMarker() != null) {
            markers.add(event.getMarker().getName());
        }
    }

    public void setMarkers(Set<String> markers) {
        this.markers = markers;
    }

    public void setThreads(Set<String> threads) {
        this.threads = threads;
    }

    public Set<String> getLoggers() {
        return loggers;
    }

    public void setLoggers(Set<String> loggers) {
        this.loggers = loggers;
    }

    public void setMdcMap(Map<String, Set<String>> mdcMap) {
        this.mdcMap = mdcMap;
    }

    public Map<String, Set<String>> getMdcMap() {
        return mdcMap;
    }

    public Set<String> getMarkers() {
        return markers;
    }

    public Set<String> getThreads() {
        return threads;
    }

    public Set<String> getNodes() {
        return nodes;
    }

    public void setNodes(Set<String> nodes) {
        this.nodes = nodes;
    }

    public Set<String> getApplications() {
        return applications;
    }

    public void setApplications(Set<String> applications) {
        this.applications = applications;
    }

    public void setCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public void setFilteredCount(int filteredCount) {
        this.filteredCount = filteredCount;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getFilteredRowCount() {
        return filteredCount;
    }
}
