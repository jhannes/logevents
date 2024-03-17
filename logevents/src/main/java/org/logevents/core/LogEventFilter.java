package org.logevents.core;

import org.logevents.LogEventObserver;
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
 * if they have a log level equal to or more severe than the {@link #getThreshold()}
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
public class LogEventFilter {

    public static LogEventFilter never() {
        return new LogEventFilter(withCondition(new LogEventPredicate.NeverCondition()));
    }

    public static EnumMap<Level, LogEventPredicate> withCondition(LogEventPredicate condition) {
        EnumMap<Level, LogEventPredicate> result = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            result.put(level, condition);
        }
        return result;
    }

    private static EnumMap<Level, LogEventPredicate> atLevel(Level threshold) {
        EnumMap<Level, LogEventPredicate> conditions = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            boolean enabled = level.toInt() >= threshold.toInt();
            conditions.put(level, enabled ? new LogEventPredicate.AlwaysCondition() : new LogEventPredicate.NeverCondition());
        }
        return conditions;
    }

    private final EnumMap<Level, LogEventPredicate> conditions;

    public LogEventFilter(EnumMap<Level, LogEventPredicate> conditions) {
        this.conditions = conditions;
    }

    public LogEventFilter(Level threshold) {
        this.conditions = atLevel(threshold);
    }

    public LogEventFilter(String filterString) {
        String[] parts = filterString.split(",");
        int start = 0;
        if (!parts[0].contains("@")) {
            start++;
            if (parts[0].trim().equals("NONE")) {
                conditions = withCondition(new LogEventPredicate.NeverCondition());
            } else {
                conditions = atLevel(Level.valueOf(parts[0]));
            }
        } else {
            conditions = withCondition(new LogEventPredicate.InheritCondition());
        }

        for (int i = start, partsLength = parts.length; i < partsLength; i++) {
            String part = parts[i];
            if (part.contains("@")) {
                addLoggingCondition(part);
            }
        }
    }

    public LogEventGenerator create(String loggerName, Level level, LogEventObserver observer) {
        return LogEventGenerator.create(loggerName, level, filterObserverOnLevel(level, observer));
    }

    private LogEventObserver filterObserverOnLevel(Level level, LogEventObserver observer) {
        return observer.filteredOn(level, getPredicate(level));
    }

    public LogEventPredicate getPredicate(Level level) {
        return conditions.get(level);
    }

    /**
     * The lowest level where this filter wants to receive events
     */
    public Level getThreshold() {
        List<Level> levels = Arrays.asList(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
        for (Level level : levels) {
            if (!(getPredicate(level) instanceof LogEventPredicate.NeverCondition)) {
                return level;
            }
        }
        return Level.TRACE;
    }

    /**
     * Parse a string like <code>INFO@mdc:key=value2|value2&mdc:key2=value</code>
     * and add the conditions to the logging rules
     */
    private void addLoggingCondition(String ruleString) {
        int atPos = ruleString.indexOf('@');
        String threshold = ruleString.substring(0, atPos);
        if (threshold.equals("NONE")) {
            LogEventPredicate condition = createLogging(ruleString.substring(atPos + 1));
            for (Level level : Level.values()) {
                conditions.put(level, getPredicate(level).and(condition.negate()));
            }
        } else {
            addLoggingCondition(Level.valueOf(threshold), ruleString.substring(atPos + 1));
        }
    }

    public void addLoggingCondition(Level level, String allRules) {
        addLoggingCondition(level, createLogging(allRules));
    }

    /**
     * Parse a string like <code>mdc:key=value2|value2&amp;mdc:key2=value</code> and return the
     * conditions to the logging rules at the given level
     */
    private LogEventPredicate createLogging(String allRules) {
        List<LogEventPredicate> allConditions = new ArrayList<>();
        for (String ruleString : allRules.split("&")) {
            allConditions.add(createLoggingCondition(ruleString));
        }
        return LogEventPredicate.allConditions(allConditions);
    }

    private LogEventPredicate createLoggingCondition(String ruleString) {
        if (ruleString.startsWith("mdc:")) {
            return createMdcCondition(ruleString);
        } else if (ruleString.startsWith("marker=")) {
            return new LogEventPredicate.RequiredMarkerCondition(ruleString);
        } else if (ruleString.startsWith("marker!=")) {
            return new LogEventPredicate.SuppressedMarkerCondition(ruleString);
        } else {
            throw new IllegalArgumentException("Unexpected rule " + ruleString);
        }
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
    public void addLoggingCondition(Level threshold, LogEventPredicate condition) {
        for (Level level : Level.values()) {
            if (level.toInt() >= threshold.toInt()) {
                conditions.put(level, getPredicate(level).or(condition));
            } else {
                conditions.put(level, getPredicate(level).and(condition.negate()));
            }
        }
    }

    public LogEventFilter withParent(LogEventFilter parentFilter) {
        EnumMap<Level, LogEventPredicate> conditions = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            conditions.put(level, getPredicate(level).withParent(parentFilter.getPredicate(level)));
        }
        return new LogEventFilter(conditions);
    }

    @Override
    public String toString() {
        return "LogEventFilter{" + conditionsToString() + '}';
    }

    private String conditionsToString() {
        return conditions.entrySet().stream()
                .filter(entry -> !(entry.getValue() instanceof LogEventPredicate.NeverCondition))
                .map(entry -> entry.getValue() instanceof LogEventPredicate.AlwaysCondition ? entry.getKey().toString() : entry.toString())
                .collect(Collectors.joining(","));
    }
}
