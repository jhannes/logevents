package org.logevents.impl;

import org.logevents.LogEventObserver;
import org.logevents.observers.ConditionalLogEventObserver;
import org.logevents.observers.LogEventPredicate;
import org.logevents.observers.NullLogEventObserver;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link LogEventObserver} that forwards all log events to a delegate observer
 * if they have a log level equal to or more severe than the {@link #minimumThreshold}
 * or if there is a {@link org.slf4j.MDC} variable on the thread which fulfills a
 * configured logging rule. There are four types of rules supported:
 * 
 * <ul>
 *     <li>Required MDC rules, eg. <code>mdc:user=admin|super</code>. Separate alternatives by |</li>
 *     <li>Suppressed MDC rules, eg. <code>mdc:user!=admin|super</code>. Separate alternatives by |</li>
 *     <li>Required marker rules, eg. <code>marker=HTTP|PERFORMANCE</code>. Separate alternatives by |</li>
 *     <li>Suppressed marker rules, eg. <code>marker!=HTTP|PERFORMANCE</code>. Separate alternatives by |</li>
 *     <li>All conditions, eg. <code>mdc:user=admin&amp;mdc:requestPath=/healthcheck</code>. Separate conditions by &amp;</li>
 * </ul>
 * 
 * <h2>Example configuration</h2>
 * <pre>
 *     logger.org.example.app=INFO,DEBUG@mdc:user=superuser|admin|tester fileObserver
 *     logger.org.example.app.database=INFO,DEBUG@mdc:user=tester&amp;mdc:requestPath!=/healthCheck&amp;marker=PERFORMANCE
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ConditionalLogEventFilter implements LogEventFilter {

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
     * Parse a string like <code>INFO@mdc:key=value2|value2&mdc:key2=value</code>
     * and add the conditions to the logging rules
     */
    private void addLoggingCondition(String ruleString) {
        int atPos = ruleString.indexOf('@');
        Level level = Level.valueOf(ruleString.substring(0, atPos));
        addLoggingCondition(level, ruleString.substring(atPos+1));
    }

    /**
     * Parse a string like <code>mdc:key=value2|value2&amp;mdc:key2=value</code> and add the
     * conditions to the logging rules at the given level
     */
    public void addLoggingCondition(Level level, String allRules) {
        List<LogEventPredicate> allConditions = new ArrayList<>();
        for (String ruleString : allRules.split("&")) {
            allConditions.add(createLoggingCondition(ruleString));
        }
        addLoggingCondition(level, LogEventPredicate.allConditions(allConditions));
    }

    private LogEventPredicate createLoggingCondition(String ruleString) {
        if (ruleString.startsWith("mdc:")) {
            return createMdcCondition(ruleString);
        } else if (ruleString.startsWith("marker=")) {
            return createRequiredMarkerCondition(ruleString);
        } else if (ruleString.startsWith("marker!=")) {
            return createSuppressedMarkerCondition(ruleString);
        } else {
            throw new IllegalArgumentException("Unexpected rule " + ruleString);
        }
    }

    private LogEventPredicate createSuppressedMarkerCondition(String ruleString) {
        return new LogEventPredicate.SuppressedMarkerCondition(ruleString);
    }

    private LogEventPredicate createRequiredMarkerCondition(String ruleString) {
        return new LogEventPredicate.RequiredMarkerCondition(ruleString);
    }

    private LogEventPredicate createMdcCondition(String ruleString) {
        String[] parts = ruleString.split("=", 2);
        String mdcKey = parts[0].substring("mdc:".length());
        Set<String> mdcValues = new HashSet<>(Arrays.asList(parts[1].split("\\|")));
        if (mdcKey.endsWith("!")) {
            return new LogEventPredicate.SuppressedMdcCondition(mdcKey.substring(0, mdcKey.length()-1), mdcValues);
        } else {
            return new LogEventPredicate.RequiredMdcCondition(mdcKey, mdcValues);
        }
    }

    /**
     * Add a sufficient condition to log at the given level
     */
    public void addLoggingCondition(Level level, LogEventPredicate condition) {
        if (level.toInt() < minimumThreshold.toInt()) {
            minimumThreshold = level;
        }
        for (Level value : Level.values()) {
            if (value.toInt() >= level.toInt()) {
                conditions.computeIfAbsent(value, v -> new ArrayList<>())
                        .add(condition);
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
