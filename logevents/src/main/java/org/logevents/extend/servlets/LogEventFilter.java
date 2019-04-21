package org.logevents.extend.servlets;

import org.logevents.LogEvent;
import org.logevents.observers.LogEventBuffer;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Queries a list of {@link LogEvent} objects by {@link #time},
 * {@link #interval}, {@link #threadName}, {@link #logger},
 * {@link #level}, {@link #markers} and {@link #mdcFilter}.
 * <p>
 *     Use {@link #test(LogEvent)} to filter a {@link java.util.stream.Stream} of
 *     {@link LogEvent}s and {@link #collectFacets(Collection)} to return a summary
 *     of which threads, loggers, markers and mdcs were used in the interval.
 * </p>
 *
 */
public class LogEventFilter implements Predicate<LogEvent> {
    private final Instant time;
    private final Duration interval;
    private final Optional<List<String>> threadName;
    private final Optional<List<String>> logger;
    private final Level level;
    private final Optional<List<Marker>> markers;
    private final Optional<Map<String, List<String>>> mdcFilter;

    @SuppressWarnings("unchecked")
    public LogEventFilter(Map untypedParameters) {
        Map<String, String[]> parameters = untypedParameters;
        Optional<Instant> instant = Optional.ofNullable(parameters.get("instant"))
                .map(t -> Instant.parse(t[0]));

        LocalDate date = Optional.ofNullable(parameters.get("date"))
                .map(p -> LocalDate.parse(p[0]))
                .orElse(LocalDate.now());

        this.time = instant.orElseGet(() -> Optional.ofNullable(parameters.get("time"))
                .map(t -> LocalTime.parse(t[0]))
                .map(time -> LocalDateTime.of(date, time))
                .map(dateTime -> dateTime.toInstant(ZoneId.systemDefault().getRules().getOffset(dateTime)))
                .orElse(Instant.now()));

        this.interval = Optional.ofNullable(parameters.get("interval"))
                .map(t -> Duration.parse(t[0]))
                .orElse(Duration.ofMinutes(10));
        this.logger = getParameter(parameters, "logger");
        this.threadName = getParameter(parameters, "thread");
        this.level = getParameter(parameters, "level").map(list -> Level.valueOf(list.get(0))).orElse(Level.INFO);
        this.markers = getParameter(parameters,"marker")
                .map(m -> m.stream().map(MarkerFactory::getMarker).collect(Collectors.toList()));

        Map<String, List<String>> mdcFilter = new HashMap<>();
        Pattern pattern = Pattern.compile("mdc\\[(.*)]");
        for (String parameterName : parameters.keySet()) {
            Matcher matcher = pattern.matcher(parameterName);
            if (matcher.matches()) {
                String[] value = parameters.get(parameterName);
                if (value.length != 0 && value[0].length() > 0) {
                    mdcFilter.put(matcher.group(1), Arrays.asList(value));
                }
            }
        }
        this.mdcFilter = mdcFilter.isEmpty() ? Optional.empty() : Optional.of(mdcFilter);
    }

    private static Optional<List<String>> getParameter(Map<String, String[]> parameters, String name) {
        String[] value = parameters.get(name);
        if (value == null || value.length == 0 || value[0].length() == 0) {
            return Optional.empty();
        }
        return Optional.of(value).map(Arrays::asList);
    }

    @Override
    public boolean test(LogEvent logEvent) {
        return threadName.map(names -> names.contains(logEvent.getThreadName())).orElse(true) &&
                logger.map(l -> l.contains(logEvent.getLoggerName())).orElse(true) &&
                markers.map(m -> m.contains(logEvent.getMarker())).orElse(true) &&
                mdcFilter.map(mdc -> matchesMdc(logEvent, mdc)).orElse(true);
    }

    private boolean matchesMdc(LogEvent logEvent, Map<String, List<String>> mdc) {
        for (Map.Entry<String, List<String>> entry : mdc.entrySet()) {
            if (!entry.getValue().contains(logEvent.getMdcProperties().get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "time=" + time +
                ", interval=" + interval +
                ", threadName=" + threadName +
                ", level=" + level +
                ", markers=" + markers +
                ", mdcFilter=" + mdcFilter +
                "}";
    }

    public Collection<LogEvent> collectMessages(LogEventBuffer logsByLevel) {
        Instant start = time.minus(interval);
        Instant end = time.plus(interval);
        return logsByLevel.filter(level, start, end);
    }

    public Map<String, Object> collectFacets(LogEventBuffer logsByLevel) {
        return collectFacets(collectMessages(logsByLevel));
    }

    Map<String, Object> collectFacets(Collection<LogEvent> events) {
        Set<String> threads = new TreeSet<>();
        Map<String, String> loggerMap = new TreeMap<>();
        Set<String> markers = new TreeSet<>();
        Map<String, Set<String>> mdcMap = new TreeMap<>();
        for (LogEvent event : events) {
            threads.add(event.getThreadName());
            loggerMap.put(event.getLoggerName(), event.getAbbreviatedLoggerName(0));
            if (event.getMarker() != null) {
                markers.add(event.getMarker().getName());
            }
            for (String mdcKey : event.getMdcProperties().keySet()) {
                mdcMap.computeIfAbsent(mdcKey, k -> new TreeSet<>()).add(event.getMdcProperties().get(mdcKey));
            }
        }
        List<Map<String, Object>> mdc = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : mdcMap.entrySet()) {
            Map<String, Object> mdcEntry = new LinkedHashMap<>();
            mdcEntry.put("name", entry.getKey());
            mdcEntry.put("values", entry.getValue());
            mdc.add(mdcEntry);
        }
        List<Map<String,String>> loggers = new ArrayList<>();
        for (Map.Entry<String, String> entry : loggerMap.entrySet()) {
            Map<String, String> logger = new HashMap<>();
            logger.put("name", entry.getKey());
            logger.put("abbreviatedName", entry.getValue());
            loggers.add(logger);
        }


        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("threads", threads);
        facets.put("loggers", loggers);
        facets.put("markers", markers);
        facets.put("mdc", mdc);
        return facets;
    }
}
