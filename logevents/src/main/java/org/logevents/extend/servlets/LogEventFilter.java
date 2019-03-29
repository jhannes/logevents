package org.logevents.extend.servlets;

import org.logevents.LogEvent;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogEventFilter implements Predicate<LogEvent> {
    private final Instant time;
    private final Duration interval;
    private final Optional<List<String>> threadName;
    private final Level level;
    private final Optional<List<Marker>> markers;
    private final Optional<Map<String, List<String>>> mdcFilter;

    public LogEventFilter(Map<String, String[]> parameters) {
        this.time = Optional.ofNullable(parameters.get("time"))
                .map(t -> LocalTime.parse(t[0]))
                .map(time -> LocalDateTime.of(LocalDate.now(), time))
                .map(dateTime -> dateTime.toInstant(ZoneId.systemDefault().getRules().getOffset(dateTime)))
                .orElse(Instant.now());
        this.interval = Optional.ofNullable(parameters.get("interval"))
                .map(t -> Duration.parse(t[0]))
                .orElse(Duration.ofMinutes(10));
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

    public Collection<LogEvent> collectMessages(Map<Level, ? extends Collection<LogEvent>> logsByLevel) {
        Instant start = time.minus(interval);
        Instant end = time.plus(interval);
        List<LogEvent> logEvents = new ArrayList<>();
        for (Map.Entry<Level, ? extends Collection<LogEvent>> entry : logsByLevel.entrySet()) {
            if (entry.getKey().compareTo(level) <= 0) {
                // TODO It may be worth the effort to implement a binary search here
                entry.getValue().stream()
                        .filter(event -> event.getInstant().isAfter(start) && event.getInstant().isBefore(end))
                        .forEach(logEvents::add);
            }
        }
        logEvents.sort(Comparator.comparing(LogEvent::getInstant));
        return logEvents;
    }

    public List<LogEvent> collect(Map<Level, ? extends Collection<LogEvent>> messages) {
        return collectMessages(messages).stream()
                .filter(this)
                .collect(Collectors.toList());
    }
}
