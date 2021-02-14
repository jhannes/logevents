package org.logevents.extend.junit;

import org.logevents.LogEvent;
import org.logevents.formatting.MessageFormatter;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class LogEventMatcherContext {
    private LogEvent event;
    private List<String> matchedFields = new ArrayList<>();
    private List<String> differingPartialFields = new ArrayList<>();
    private List<String> differingExactFields = new ArrayList<>();
    private Map<String, Object> expected = new HashMap<>();
    private Map<String, Object> actual = new HashMap<>();

    public LogEventMatcherContext(LogEvent event) {
        this.event = event;
    }

    public LogEventMatcherContext(LogEvent event, LogEventMatcher matcher) {
        this(event);
        matcher.apply(this);
    }

    public LogEvent getEvent() {
        return event;
    }

    public LogEventMatcherContext compareFields(String fieldName, Object actual, Object expected, boolean requiredForPartialMatch) {
        this.expected.put(fieldName, expected);
        this.actual.put(fieldName, actual);
        if (Objects.equals(actual, expected)) {
            matchedFields.add(fieldName);
        } else if (requiredForPartialMatch) {
            differingPartialFields.add(fieldName);
        } else {
            differingExactFields.add(fieldName);
        }
        return this;
    }

    public List<String> getMatchedFields() {
        return matchedFields;
    }

    public List<String> getDifferingFields() {
        List<String> differingFields = new ArrayList<>();
        differingFields.addAll(this.differingPartialFields);
        differingFields.addAll(this.differingExactFields);
        return differingFields;
    }

    public boolean isPartialMatch() {
        return differingPartialFields.isEmpty();
    }

    public boolean isExactMatch() {
        return isPartialMatch() && differingExactFields.isEmpty();
    }

    public LogEventMatcherContext level(Level level) {
        return compareFields("level", getEvent().getLevel(), level, true);
    }

    public LogEventMatcherContext logger(String loggerName) {
        return compareFields("logger", getEvent().getLoggerName(), loggerName, true);
    }

    public LogEventMatcherContext logger(Class<?> loggerClass) {
        return logger(loggerClass.getName());
    }

    public LogEventMatcherContext pattern(String message) {
        return compareFields("message", getEvent().getMessage(), message, false);
    }

    public LogEventMatcherContext formattedMessage(String formattedMessage) {
        String message = getEvent().getMessage(new MessageFormatter());
        return compareFields("formattedMessage", message, formattedMessage, true);
    }

    public LogEventMatcherContext args(Object... args) {
        if (Arrays.equals(getEvent().getArgumentArray(), args)) {
            matchedFields.add("arguments");
        } else {
            differingExactFields.add("arguments");
        }
        return this;
    }

    public LogEventMatcherContext argument(int index, Object argument) {
        if (getEvent().getArgumentArray().length <= index) {
            differingExactFields.add("arguments[" + index + "]");
            this.expected.put("arguments[" + index + "]", argument);
            this.actual.put("arguments[" + index + "]", "<missing>");
            return this;
        } else {
            return compareFields("arguments[" + index + "]", getEvent().getArgumentArray()[index], argument, false);
        }
    }

    public LogEventMatcherContext exception(Class<? extends Throwable> throwableClass) {
        Throwable throwable = getEvent().getThrowable();
        if (throwable == null) {
            return compareFields("throwable", null, throwableClass, false);
        } else {
            return compareFields("throwable", throwable.getClass(), throwableClass, false);
        }
    }

    public LogEventMatcherContext exception(Class<? extends Throwable> throwableClass, String message) {
        Throwable throwable = getEvent().getThrowable();
        if (throwable == null) {
            return compareFields("throwable", null, throwableClass, false);
        } else {
            if (throwable.getClass() != throwableClass) {
                return compareFields("throwable", throwable.getClass(), throwableClass, false);
            } else {
                return compareFields("throwable", throwable.getMessage(), message, false);
            }
        }
    }

    public LogEventMatcherContext noException() {
        return compareFields("throwable", getEvent().getThrowable(), null, false);
    }

    public String diff() {
        return getDifferingFields().stream()
                .map(field -> String.format("%s expected: [%s] but was [%s]", field, expected.get(field), actual.get(field)))
                .collect(Collectors.joining("; "));
    }

    public String describePartialMatch() {
        return matchedFields.stream()
                .map(field -> String.format("%s: [%s]", field, actual.get(field)))
                .collect(Collectors.joining("; "));
    }
}
