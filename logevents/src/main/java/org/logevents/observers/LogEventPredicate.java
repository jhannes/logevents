package org.logevents.observers;

import org.logevents.LogEvent;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A test to check whether a log message fulfills some condition
 */
public interface LogEventPredicate extends Predicate<LogEvent> {

    static LogEventPredicate allConditions(List<LogEventPredicate> allConditions) {
        if (allConditions.size() > 1) {
            return new AllConditions(allConditions);
        } else if (allConditions.isEmpty()) {
            return new NullCondition();
        } else {
            return allConditions.get(0);
        }
    }

    /**
     * Called from {@link Logger#isDebugEnabled()}, {@link Logger#isInfoEnabled()}
     * etc. Can be used to short circuit logging based on MDC values in addition
     * to log level.
     */
    boolean test();

    /**
     * Called from {@link Logger#isDebugEnabled(Marker)}
     * {@link Logger#isInfoEnabled(Marker)} etc. Can be used to short circuit
     * logging based on Markers and MDC values in addition to log level.
     */
    default boolean test(Marker marker) {
        return test();
    }

    class NullCondition implements LogEventPredicate {
        @Override
        public boolean test() {
            return true;
        }

        @Override
        public boolean test(LogEvent logEvent) {
            return true;
        }
    }

    /**
     * A rule specifying that any log message will be logged if they they have an
     * MDC value with the key where the value matches one of the accepted values
     */
    class RequiredMdcCondition implements LogEventPredicate {
        private final String mdcKey;
        private final Set<String> acceptedValues;

        public RequiredMdcCondition(String ruleString) {
            String[] parts = ruleString.split("=", 2);
            this.mdcKey = parts[0].substring("mdc:".length());
            this.acceptedValues = new HashSet<>(Arrays.asList(parts[1].split("\\|")));
        }

        public RequiredMdcCondition(String mdcKey, String values) {
            this.mdcKey = mdcKey;
            this.acceptedValues = new HashSet<>(Arrays.asList(values.split("\\|")));
        }

        @Override
        public boolean test(LogEvent event) {
            return event.getMdcProperties().containsKey(mdcKey) && acceptedValues.contains(event.getMdcProperties().get(mdcKey));
        }

        @Override
        public boolean test() {
            return MDC.get(mdcKey) != null && acceptedValues.contains(MDC.get(mdcKey));
        }

        @Override
        public String toString() {
            return "RequiredMdcCondition{" + mdcKey + " in " + acceptedValues + '}';
        }
    }

    class SuppressedMdcCondition implements LogEventPredicate {
        private final String mdcKey;
        private final Set<String> rejectedValues;

        public SuppressedMdcCondition(String mdcKey, String values) {
            this.mdcKey = mdcKey;
            this.rejectedValues = new HashSet<>(Arrays.asList(values.split("\\|")));
        }

        @Override
        public boolean test(LogEvent event) {
            return !event.getMdcProperties().containsKey(mdcKey) || !rejectedValues.contains(event.getMdcProperties().get(mdcKey));
        }

        @Override
        public boolean test() {
            return MDC.get(mdcKey) == null || !rejectedValues.contains(MDC.get(mdcKey));
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" + mdcKey + " NOT in " + rejectedValues + '}';
        }
    }

    /**
     * Abstract superclass for rules about markers
     */
    abstract class MarkerCondition implements LogEventPredicate {

        public List<Marker> markerAlternatives;

        public MarkerCondition(List<Marker> markers) {
            this.markerAlternatives = markers;
        }

        public MarkerCondition(String markers) {
            this(Arrays.stream(markers.split("\\|"))
                    .map(MarkerFactory::getMarker)
                    .collect(Collectors.toList()));
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

    /**
     * A rule specifying that any log message will be logged if they must have one of
     * the required markers or a marker that contains one of the required markers.
     */
    class RequiredMarkerCondition extends MarkerCondition {

        public RequiredMarkerCondition(String ruleString) {
            super(ruleString.substring("marker=".length()));
            if (!ruleString.startsWith("marker=")) {
                throw new IllegalArgumentException("Unexpected rule " + ruleString);
            }
        }

        public RequiredMarkerCondition(List<Marker> requireMarkers) {
            super(requireMarkers);
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

    /**
     * A rule specifying that log message will be logged if they don't have one
     * of the suppressed markers or a marker containing any of the suppressed markers 
     */
    class SuppressedMarkerCondition extends MarkerCondition {

        public SuppressedMarkerCondition(String ruleString) {
            super(ruleString.substring("marker!=".length()));
            if (!ruleString.startsWith("marker!=")) {
                throw new IllegalArgumentException("Unexpected rule " + ruleString);
            }
        }

        public SuppressedMarkerCondition(List<Marker> suppressedMarkers) {
            super(suppressedMarkers);
        }

        @Override
        public boolean test(Marker marker) {
            return marker == null || markerAlternatives.stream().noneMatch(marker::contains);
        }

        @Override
        public boolean test() {
            return true;
        }
    }

    /**
     * A rule specifying that any log message will be logged if they must fulfill
     * at least one of the specified conditions. This is used when there are multiple
     * conditions that all should allow a message to be loaded
     */
    class AnyCondition implements LogEventPredicate {
        private final Collection<LogEventPredicate> predicates;

        AnyCondition(Collection<LogEventPredicate> predicates) {
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

        @Override
        public String toString() {
            return predicates.stream().map(Object::toString).collect(Collectors.joining(" OR "));
        }
    }

    /**
     * A rule specifying that any log message will be logged if they must fulfill
     * all of the specified conditions. This is used to specify for example that you
     * only want to log messages with a get of MDC variables to be logged 
     */
    class AllConditions implements LogEventPredicate {
        private final Collection<LogEventPredicate> predicates;

        public AllConditions(Collection<LogEventPredicate> predicates) {
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
            return predicates.stream().map(Object::toString).collect(Collectors.joining(" AND "));
        }
    }
}
