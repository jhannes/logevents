package org.logevents.core;

import org.logevents.LogEvent;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.ArrayList;
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
            return new AlwaysCondition();
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

    /**
     * Called from {@link Logger#error}, {@link Logger#warn}, {@link Logger#info}
     * {@link Logger#debug} and {@link Logger#trace}. Can be used to short circuit
     * logging based on event-specific values in addition to log level.
     */
    @Override
    default boolean test(LogEvent logEvent) {
        return test();
    }

    LogEventPredicate negate();

    @Override
    default LogEventPredicate or(Predicate<? super LogEvent> other) {
        if (other instanceof NeverCondition) {
            return this;
        } else if (other instanceof AlwaysCondition) {
            return new AlwaysCondition();
        }
        return new AnyCondition(Arrays.asList(this, (LogEventPredicate) other));
    }

    @Override
    default LogEventPredicate and(Predicate<? super LogEvent> other) {
        if (other instanceof AlwaysCondition) {
            return this;
        } else if (other instanceof NeverCondition) {
            return new NeverCondition();
        }
        return new AllConditions(Arrays.asList(this, (LogEventPredicate) other));
    }

    default LogEventPredicate withParent(LogEventPredicate parent) {
        return this;
    }

    class AlwaysCondition implements LogEventPredicate {
        @Override
        public boolean test() {
            return true;
        }

        @Override
        public LogEventPredicate negate() {
            return new NeverCondition();
        }

        @Override
        public AlwaysCondition or(Predicate<? super LogEvent> other) {
            return this;
        }

        @Override
        public LogEventPredicate and(Predicate<? super LogEvent> other) {
            return (LogEventPredicate) other;
        }

        @Override
        public String toString() {
            return "Always";
        }
    }

    class NeverCondition implements LogEventPredicate {

        @Override
        public boolean test() {
            return false;
        }

        @Override
        public LogEventPredicate negate() {
            return new AlwaysCondition();
        }

        @Override
        public LogEventPredicate and(Predicate<? super LogEvent> other) {
            return this;
        }

        @Override
        public LogEventPredicate or(Predicate<? super LogEvent> other) {
            return (LogEventPredicate) other;
        }

        @Override
        public String toString() {
            return "Never";
        }
    }

    /**
     * A rule specifying that any log message will be logged if they they have an
     * MDC value with the key where the value matches one of the accepted values
     */
    class RequiredMdcCondition implements LogEventPredicate {
        private final String mdcKey;
        private final Set<String> acceptedValues;

        public RequiredMdcCondition(String mdcKey, String values) {
            this(mdcKey, new HashSet<>(Arrays.asList(values.split("\\|\\s*"))));
        }

        public RequiredMdcCondition(String mdcKey, Set<String> acceptedValues) {
            this.mdcKey = mdcKey;
            this.acceptedValues = acceptedValues;
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
        public LogEventPredicate negate() {
            return new SuppressedMdcCondition(mdcKey, acceptedValues);
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
            this(mdcKey, new HashSet<>(Arrays.asList(values.split("\\|\\s*"))));
        }

        public SuppressedMdcCondition(String mdcKey, Set<String> acceptedValues) {
            this.mdcKey = mdcKey;
            this.rejectedValues = acceptedValues;
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
        public LogEventPredicate negate() {
            return new RequiredMdcCondition(mdcKey, rejectedValues);
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

        public Set<Marker> markerAlternatives;

        public MarkerCondition(Set<Marker> markers) {
            this.markerAlternatives = markers;
        }

        public MarkerCondition(String markers) {
            this(Arrays.stream(markers.split("\\|"))
                    .map(MarkerFactory::getMarker)
                    .collect(Collectors.toSet()));
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
        }

        public RequiredMarkerCondition(Set<Marker> requireMarkers) {
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

        @Override
        public LogEventPredicate negate() {
            return new SuppressedMarkerCondition(markerAlternatives);
        }

        @Override
        public LogEventPredicate and(Predicate<? super LogEvent> other) {
            if (other instanceof RequiredMarkerCondition) {
                Set<Marker> overlap = new HashSet<>();
                Set<Marker> otherMarkers = ((RequiredMarkerCondition) other).markerAlternatives;
                otherMarkers.stream()
                        .filter(m -> markerAlternatives.stream().anyMatch(m::contains))
                        .forEach(overlap::add);
                markerAlternatives.stream()
                        .filter(m -> otherMarkers.stream().anyMatch(m::contains))
                        .forEach(overlap::add);
                return overlap.isEmpty() ? new NeverCondition() : new RequiredMarkerCondition(overlap);
            } else if (other instanceof SuppressedMarkerCondition) {
                return this;
            }
            return super.and(other);
        }
    }

    /**
     * A rule specifying that log message will be logged if they don't have one
     * of the suppressed markers or a marker containing any of the suppressed markers
     */
    class SuppressedMarkerCondition extends MarkerCondition {

        public SuppressedMarkerCondition(String ruleString) {
            super(ruleString.substring("marker!=".length()));
        }

        public SuppressedMarkerCondition(Set<Marker> suppressedMarkers) {
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

        @Override
        public LogEventPredicate negate() {
            return new RequiredMarkerCondition(markerAlternatives);
        }

        @Override
        public LogEventPredicate and(Predicate<? super LogEvent> other) {
            if (other instanceof SuppressedMarkerCondition) {
                Set<Marker> suppressedMarkers = new HashSet<>(this.markerAlternatives);
                suppressedMarkers.addAll(((SuppressedMarkerCondition)other).markerAlternatives);
                return new SuppressedMarkerCondition(suppressedMarkers);
            }
            return super.and(other);
        }

        @Override
        public LogEventPredicate or(Predicate<? super LogEvent> other) {
            if (other instanceof RequiredMarkerCondition) {
                return this;
            } else if (other instanceof SuppressedMarkerCondition) {
                Set<Marker> suppressedMarkers = new HashSet<>(this.markerAlternatives);
                suppressedMarkers.addAll(((SuppressedMarkerCondition)other).markerAlternatives);
                return new SuppressedMarkerCondition(suppressedMarkers);
            }
            return super.or(other);
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
        public LogEventPredicate withParent(LogEventPredicate parent) {
            return predicates.stream().reduce(new NeverCondition(), (a, b) -> a.withParent(parent).or(b.withParent(parent)));
        }

        @Override
        public String toString() {
            return predicates.stream().map(Object::toString).collect(Collectors.joining(" OR "));
        }

        @Override
        public LogEventPredicate negate() {
            throw new IllegalArgumentException();
        }

        @Override
        public LogEventPredicate or(Predicate<? super LogEvent> other) {
            List<LogEventPredicate> predicates = new ArrayList<>(this.predicates);
            predicates.add((LogEventPredicate) other);
            return new AnyCondition(predicates);
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

        @Override
        public LogEventPredicate withParent(LogEventPredicate parent) {
            return predicates.stream().reduce(new AlwaysCondition(), (a, b) -> a.withParent(parent).and(b.withParent(parent)));
        }

        @Override
        public LogEventPredicate negate() {
            return new NotAllCondition(predicates);
        }
    }

    class NotAllCondition implements LogEventPredicate {

        private final Collection<LogEventPredicate> predicates;

        public NotAllCondition(Collection<LogEventPredicate> predicates) {
            this.predicates = predicates;
        }

        @Override
        public boolean test() {
            return !predicates.stream().allMatch(LogEventPredicate::test);
        }

        @Override
        public boolean test(LogEvent logEvent) {
            return !predicates.stream().allMatch(p -> p.test(logEvent));
        }

        @Override
        public boolean test(Marker marker) {
            return !predicates.stream().allMatch(p -> p.test(marker));
        }

        @Override
        public LogEventPredicate negate() {
            return new AllConditions(predicates);
        }

        @Override
        public String toString() {
            return "(NOT " + predicates.stream().map(Object::toString).collect(Collectors.joining(" AND ")) + ")";
        }
    }

    class InheritCondition implements LogEventPredicate {
        @Override
        public boolean test() {
            return false;
        }

        @Override
        public LogEventPredicate negate() {
            return new AlwaysCondition();
        }

        @Override
        public boolean test(LogEvent logEvent) {
            return false;
        }

        @Override
        public String toString() {
            return "Inherit";
        }

        @Override
        public LogEventPredicate withParent(LogEventPredicate parent) {
            return parent;
        }
    }
}
