package org.logevents.extend.junit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.impl.StaticLoggerBinder;

public class ExpectedLogEventsRule implements TestRule, LogEventObserver {

    private static class ExpectedLogEvent {

        private String loggerName;
        private Level level;
        private String messagePattern;

        public ExpectedLogEvent(String loggerName, Level level, String messagePattern) {
            this.loggerName = loggerName;
            this.level = level;
            this.messagePattern = messagePattern;
        }

        public boolean exactMatch(LogEvent event) {
            return event.getLoggerName().equals(loggerName)
                    && event.getLevel() == level
                    && event.getMessage().equals(messagePattern);
        }

        public boolean partialMatch(LogEvent event) {
            return event.getLoggerName().equals(loggerName)
                    && event.getLevel().toInt() <= level.toInt();
        }

    }

    private Logger logger;
    private List<ExpectedLogEvent> filters = new ArrayList<>();
    private List<LogEvent> events = new ArrayList<>();

    @Override
    public void logEvent(LogEvent logEvent) {
        if (filters.stream().anyMatch(p -> p.partialMatch(logEvent))) {
            events.add(logEvent);
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                LogEventFactory loggerFactory = (LogEventFactory) LoggerFactory.getILoggerFactory();
                logger = loggerFactory.getRootLogger();

                Level oldLevel = loggerFactory.setLevel(logger, Level.TRACE);
                LogEventObserver oldObserver = loggerFactory.setObserver(logger, ExpectedLogEventsRule.this, false);
                try {
                    base.evaluate();
                } finally {
                    loggerFactory.setLevel(logger, oldLevel);
                    loggerFactory.setObserver(logger, oldObserver, true);
                    verifyCompletion();
                }
            }
        };
    }

    public void expect(Class<?> logClass, Level level, String messagePattern) {
        expect(logClass.getName(), level, messagePattern);
    }

    public void expect(String loggerName, Level level, String messagePattern) {
        this.filters.add(new ExpectedLogEvent(loggerName, level, messagePattern));
    }

    public void verifyCompletion() {
        try {
            Optional<ExpectedLogEvent> firstMissedFilter = filters.stream()
                    .filter(filter -> this.events.stream().noneMatch(filter::exactMatch))
                    .findAny();
            firstMissedFilter.ifPresent(filter -> Assert.fail(failureMessage(filter)));
        } finally {
            filters.clear();
            events.clear();
        }
    }

    private String failureMessage(ExpectedLogEvent filter) {
        List<String> applicableMatches = events.stream()
                .filter(filter::partialMatch)
                .map(LogEvent::getMessage)
                .collect(Collectors.toList());
        return "Expected message not logged: " + filter.loggerName + " [" + filter.level + "]: " + filter.messagePattern
                +  ". Applicable matches: " + applicableMatches;
    }

}
