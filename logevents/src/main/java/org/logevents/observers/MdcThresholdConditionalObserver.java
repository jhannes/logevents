package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link LogEventObserver} that forwards all log events to a delegate observer
 * if they have a log level equal to or more severe than the {@link #minimumThreshold}
 * or if there is a {@link org.slf4j.MDC} variable on the thread which fulfills a
 * configured logging rule
 * 
 * <h2>Example configuration</h2>
 * <pre>
 *     logger.org.example.app=INFO,DEBUG@mdc:user=superuser,admin,tester fileObserver
 *     logger.org.example.app.database=INFO,DEBUG@mdc:user=tester fileObserver
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class MdcThresholdConditionalObserver implements LogEventObserver {

    /**
     * A rule specifying that if the given MDC has one of the allowedValues,
     * the logging threshold shoud be as specified
     */
    private static class MdcFilterRule {
        public final Set<String> allowedValues;
        public final Level threshold;

        private MdcFilterRule(Set<String> allowedValues, Level threshold) {
            this.allowedValues = allowedValues;
            this.threshold = threshold;
        }

        private boolean matchRule(LogEvent event, String mdcValue) {
            return mdcValue != null && allowedValues.contains(mdcValue) && event.getLevel().toInt() >= threshold.toInt();
        }
    } 
    
    private final LogEventObserver delegate;
    private Level minimumThreshold;
    private final Level defaultThreshold;
    private final Map<String, MdcFilterRule> rules = new HashMap<>();

    public MdcThresholdConditionalObserver(String filter, LogEventObserver delegate) {
        this.delegate = delegate;
        
        String[] parts = filter.split(",");
        this.defaultThreshold = Level.valueOf(parts[0]);
        this.minimumThreshold = this.defaultThreshold;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            addMdcRule(part);
        }
    }

    /**
     * Parse a string like INFO@mdc:key=value2|value2
     */
    private void addMdcRule(String ruleString) {
        int atPos = ruleString.indexOf('@');
        Level level = Level.valueOf(ruleString.substring(0, atPos));
        int equalPos = ruleString.indexOf('=', atPos+1);
        String mdcKey = ruleString.substring(atPos+1 + "mdc:".length(), equalPos);
        List<String> allowedValues = Arrays.asList(ruleString.substring(equalPos+1).split("\\|"));
        addMdcFilter(level, mdcKey, allowedValues);
    }

    public MdcThresholdConditionalObserver(LogEventObserver delegate, Level threshold) {
        this.delegate = delegate;
        this.defaultThreshold = threshold;
        this.minimumThreshold = threshold;
    }

    public Level getMinimumThreshold() {
        return minimumThreshold;
    }

    @Override
    public void logEvent(LogEvent event) {
        for (Map.Entry<String, MdcFilterRule> rule : rules.entrySet()) {
            if (rule.getValue().matchRule(event, event.getMdcProperties().get(rule.getKey()))) {
                delegate.logEvent(event);
                return;
            }
        }
    }

    @Override
    public LogEventObserver filteredOn(Level level, Level configuredThreshold) {
        if (configuredThreshold == null || configuredThreshold.compareTo(level) < 0) {
            return new NullLogEventObserver();
        } else if (minimumThreshold.toInt() > level.toInt()) {
            return new NullLogEventObserver();
        } else if (defaultThreshold.toInt() <= level.toInt()) {
            return delegate;
        }
        return this;
    }

    public void addMdcFilter(Level level, String mdcKey, List<String> allowedValues) {
        if (level.toInt() < minimumThreshold.toInt()) {
            minimumThreshold = level;
        }
        rules.put(mdcKey, new MdcFilterRule(new HashSet<>(allowedValues), level));
    }
}
