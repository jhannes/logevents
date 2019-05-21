package org.logevents.query;

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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Queries a list of {@link LogEvent} objects by time, interval, {@link #threadName},
 * {@link #logger}, {@link #level}, {@link #markers} and {@link #mdcFilter}.
 * <p>
 *     Use {@link #test(LogEvent)} to filter a {@link Stream} of
 *     {@link LogEvent}s .
 * </p>
 *
 */
public class LogEventFilter implements Predicate<LogEvent> {

    private final Instant startTime;
    private final Instant endTime;
    private final Level level;
    private final Optional<List<String>> threadName;
    private final Optional<List<String>> logger;
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

        ZoneOffset timezoneOffset = Optional.ofNullable(parameters.get("timezoneOffset"))
                .map(tzOffset -> -Integer.parseInt(tzOffset[0]))
                .map(tzOffset -> ZoneOffset.ofHoursMinutes(tzOffset / 60, tzOffset % 60))
                .orElse(ZoneId.systemDefault().getRules().getOffset(date.atStartOfDay()));

        Instant time = instant.orElseGet(() -> Optional.ofNullable(parameters.get("time"))
                .map(t -> LocalTime.parse(t[0]))
                .map(t -> LocalDateTime.of(date, t).toInstant(timezoneOffset))
                .orElse(Instant.now()));

        Duration interval = Optional.ofNullable(parameters.get("interval"))
                .map(t -> Duration.parse(t[0]))
                .orElse(Duration.ofMinutes(10));
        this.logger = getParameter(parameters, "logger");
        this.threadName = getParameter(parameters, "thread");
        this.level = getParameter(parameters, "level")
                .map(list -> Level.valueOf(list.get(0)))
                .orElse(Level.INFO);
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
        startTime = time.minus(interval);
        endTime = time.plus(interval);
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
                "startTime=" + startTime +
                ", endTime=" + endTime +
                ", threadName=" + threadName +
                ", level=" + level +
                ", markers=" + markers +
                ", mdcFilter=" + mdcFilter +
                "}";
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Level getThreshold() {
        return level;
    }
}
