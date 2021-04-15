package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.impl.LogEventFilter;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A {@link LogEventObserver} that forwards all log events to a delegate observer
 * if they have a log level equal to or more severe than the {@link #minimumThreshold}
 * or if there is a {@link org.slf4j.MDC} variable on the thread which fulfills a
 * configured logging rule. There are four types of rules supported:
 * 
 * <ul>
 *     <li>MDC rules, eg. <code>mdc:user=admin|super</code>. Separate alternatives by |</li>
 *     <li>Required marker rules, eg. <code>marker=HTTP|PERFORMANCE</code>. Separate alternatives by |</li>
 *     <li>Suppressed marker rules, eg. <code>marker!=HTTP|PERFORMANCE</code>. Separate alternatives by |</li>
 *     <li>All conditions, eg. <code>mdc:user=admin&mdc:requestPath=/healthcheck</code>. Separate conditions by &</li>
 * </ul>
 * 
 * <h2>Example configuration</h2>
 * <pre>
 *     logger.org.example.app=INFO,DEBUG@mdc:user=superuser,admin,tester fileObserver
 *     logger.org.example.app.database=INFO,DEBUG@mdc:user=tester&marker=PERFORMANCE
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ConditionalLogEventFilter implements LogEventFilter {

    private interface LogEventPredicate extends Predicate<LogEvent> {
        boolean test();

        boolean test(Marker marker);
    }
    
    /**
     * A rule specifying that if the given MDC has one of the allowedValues,
     * it should be logged
     */
    private static class MdcCondition implements LogEventPredicate {
        private final String mdcVariable;
        private final Set<String> acceptedValues;

        public MdcCondition(String ruleString) {
            String[] parts = ruleString.split("=", 2);
            this.mdcVariable = parts[0].substring("mdc:".length());
            this.acceptedValues = new HashSet<>(Arrays.asList(parts[1].split("\\|")));
        }

        @Override
        public boolean test(LogEvent event) {
            return event.getMdcProperties().containsKey(mdcVariable) && acceptedValues.contains(event.getMdcProperties().get(mdcVariable));
        }

        @Override
        public boolean test() {
            return MDC.get(mdcVariable) != null && acceptedValues.contains(MDC.get(mdcVariable));
        }

        @Override
        public boolean test(Marker marker) {
            return test();
        }

        @Override
        public String toString() {
            return "MdcCondition{" + mdcVariable + " in " + acceptedValues + '}';
        }
    }
    
    private abstract static class MarkerCondition implements LogEventPredicate {

        public List<Marker> markerAlternatives;

        public MarkerCondition(String markers) {
            markerAlternatives = Arrays.stream(markers.split("\\|"))
                    .map(MarkerFactory::getMarker)
                    .collect(Collectors.toList());
        }

        @Override
        public boolean test(LogEvent logEvent) {
            return test(logEvent.getMarker());
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" +
                    markerAlternatives.stream().map(Object::toString).collect(Collectors.joining("|")) + 
            "}";
        }
    }
    
    public static class RequiredMarkerCondition extends MarkerCondition {

        public RequiredMarkerCondition(String ruleString) {
            super(ruleString.substring("marker=".length()));
            if (!ruleString.startsWith("marker=")) {
                throw new IllegalArgumentException("Unexpected rule " + ruleString);
            }
        }

        @Override
        public boolean test(Marker marker) {
            return marker != null && markerAlternatives.stream().anyMatch(marker::contains);
        }

        @Override
        public boolean test() {
            return false;
        }

    }

    public static class SuppressedMarkerCondition extends MarkerCondition {

        public SuppressedMarkerCondition(String ruleString) {
            super(ruleString.substring("marker!=".length()));
            if (!ruleString.startsWith("marker!=")) {
                throw new IllegalArgumentException("Unexpected rule " + ruleString);
            }
        }

        @Override
        public boolean test(Marker marker) {
            return marker != null && markerAlternatives.stream().noneMatch(marker::contains);
        }

        @Override
        public boolean test() {
            return true;
        }
    }
    
    
    private static class AnyCondition implements LogEventPredicate {
        private final Collection<LogEventPredicate> predicates;

        private AnyCondition(Collection<LogEventPredicate> predicates) {
            this.predicates = predicates;
        }

        @Override
        public boolean test() {
            return predicates.stream().anyMatch(LogEventPredicate::test);
        }

        @Override
        public boolean test(Marker marker) {
            return predicates.stream().anyMatch(logEventPredicate -> logEventPredicate.test(marker));
        }

        @Override
        public boolean test(LogEvent event) {
            return predicates.stream().anyMatch(p -> p.test(event));
        }
    }    
    
    private static class AllConditions implements LogEventPredicate {
        private final Collection<LogEventPredicate> predicates;

        private AllConditions(Collection<LogEventPredicate> predicates) {
            this.predicates = predicates;
        }

        @Override
        public boolean test() {
            return predicates.stream().allMatch(LogEventPredicate::test);
        }

        @Override
        public boolean test(Marker marker) {
            return predicates.stream().allMatch(logEventPredicate -> logEventPredicate.test(marker));
        }

        @Override
        public boolean test(LogEvent event) {
            return predicates.stream().allMatch(p -> p.test(event));
        }

        @Override
        public String toString() {
            return predicates.stream().map(Object::toString).collect(Collectors.joining(" & "));
        }
    }
    
    private static class ConditionalLogEventObserver implements LogEventObserver {
        private final LogEventObserver delegate;
        
        private final LogEventPredicate condition;

        private ConditionalLogEventObserver(LogEventObserver delegate, List<LogEventPredicate> mdcConditions) {
            this.delegate = delegate;
            this.condition = mdcConditions.size() > 1 ? new AnyCondition(mdcConditions) : mdcConditions.get(0);
        }

        @Override
        public void logEvent(LogEvent logEvent) {
            if (condition.test(logEvent)) {
                this.delegate.logEvent(logEvent);
            }
        }

        @Override
        public boolean isEnabled() {
            return condition.test() && delegate.isEnabled();
        }

        @Override
        public boolean isEnabled(Marker marker) {
            return condition.test(marker) && delegate.isEnabled(marker);
        }
    }

    @Override
    public LogEventObserver filterObserverOnLevel(Level level, LogEventObserver observer) {
        if (defaultThreshold != null && level.toInt() >= defaultThreshold.toInt()) {
            return observer.filteredOn(level, getThreshold());
        } else if (conditions.get(level) == null || conditions.get(level).isEmpty()) {
            return new NullLogEventObserver();
        } else {
            return new ConditionalLogEventObserver(observer, conditions.get(level));
        }
    }

    @Override
    public Level getThreshold() {
        return minimumThreshold;
    }

    private Level minimumThreshold = Level.ERROR;
    private final Level defaultThreshold;
    private final EnumMap<Level, List<LogEventPredicate>> conditions = new EnumMap<>(Level.class);

    public ConditionalLogEventFilter(Level threshold) {
        this.defaultThreshold = threshold;
        this.minimumThreshold = threshold;
    }

    public ConditionalLogEventFilter(String filter) {
        Level defaultThreshold = null;
        String[] parts = filter.split(",");
        for (String part : parts) {
            if (part.contains("@")) {
                addLoggingCondition(part);
            } else {
                defaultThreshold = Level.valueOf(part);
                if (minimumThreshold.toInt() >= defaultThreshold.toInt()) {
                    this.minimumThreshold = defaultThreshold;
                }
            }
        }
        this.defaultThreshold = defaultThreshold;
    }

    /**
     * Parse a string like INFO@mdc:key=value2|value2&mdc:key2=value
     */
    private void addLoggingCondition(String ruleString) {
        int atPos = ruleString.indexOf('@');
        Level level = Level.valueOf(ruleString.substring(0, atPos));
        addLoggingCondition(level, ruleString.substring(atPos+1));
    }

    public void addLoggingCondition(Level level, String allRules) {
        List<LogEventPredicate> allConditions = new ArrayList<>();
        for (String ruleString : allRules.split("&")) {
            allConditions.add(createLoggingCondition(ruleString));
        }
        if (allConditions.size() > 1) {
            addLoggingCondition(level, new AllConditions(allConditions));
        } else {
            addLoggingCondition(level, allConditions.get(0));
        }
    }

    private LogEventPredicate createLoggingCondition(String ruleString) {
        if (ruleString.startsWith("mdc:")) {
            return new MdcCondition(ruleString);
        } else if (ruleString.startsWith("marker=")) {
            return new RequiredMarkerCondition(ruleString);
        } else if (ruleString.startsWith("marker!=")) {
            return new SuppressedMarkerCondition(ruleString);
        } else {
            throw new IllegalArgumentException("Unexpected rule " + ruleString);
        }
    }

    public void addLoggingCondition(Level level, LogEventPredicate condition) {
        if (level.toInt() < minimumThreshold.toInt()) {
            minimumThreshold = level;
        }
        for (Level value : Level.values()) {
            if (value.toInt() >= level.toInt()) {
                conditions.computeIfAbsent(value, v -> new ArrayList<>()).add(condition);
            }
        }
    }

    @Override
    public String toString() {
        return "ConditionalLogEventFilter{" +
                (defaultThreshold != null ? (defaultThreshold.name() + ",") : "") + 
                conditionsToString() + '}';
    }

    private String conditionsToString() {
        return conditions.entrySet().stream()
                .filter(c -> !c.getValue().isEmpty())
                .filter(c -> defaultThreshold == null || c.getKey().toInt() > defaultThreshold.toInt())
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }
}
