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
import org.logevents.formatting.MessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * A JUnit @Rule class that can be used to verify that only expected messages are logged.
 *
 * <h2>Example</h2>
 * <pre>
 * public class ExampleTest {
 *    private static final Logger logger = LoggerFactory.getLogger(ExampleTest.class)
 *
 *    Rule
 *    public ExpectedLogEventsRule expectLogEvents = new ExpectedLogEventsRule(Level.WARN);
 *
 *    Test
 *    public void willFailBecauseOfUnexpectedLogEvent() {
 *        logger.warn("Whoa there!");
 *    }
 *
 *    Test
 *    public void willFailBecauseExpectedEventWasNotLogged() {
 *        expectLogEvents.expect(ExampleTest.class, Level.WARN, "Whoa there!");
 *    }
 *
 *    Test
 *    public void willFailBecauseLogMessageDidNotMatch() {
 *        expectLogEvents.expect(ExampleTest.class, Level.WARN, "Whoa there!");
 *        logger.warn("Another message!");
 *    }
 *
 *    Test
 *    public void willPass() {
 *        expectLogEvents.expect(ExampleTest.class, Level.WARN, "Whoa there!");
 *        logger.warn("Whoa there!");
 *    }
 * }
 * </pre>
 *
 *
 */
public class ExpectedLogEventsRule implements TestRule, LogEventObserver {

    private Level threshold;

    public ExpectedLogEventsRule(Level threshold) {
        this.threshold = threshold;
    }

    private static class ExpectedLogEventPattern {

        String loggerName;
        Level level;
        String messagePattern;

        public ExpectedLogEventPattern(String loggerName, Level level, String messagePattern) {
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
            return event.getLoggerName().equals(loggerName) && !event.isBelowThreshold(level);
        }

        public String asString(LogEvent event) {
            return event.getMessage();
        }

        public String describe() {
            return loggerName + " [" + level + "]: \"" + messagePattern + "\"";
        }
    }

    private static MessageFormatter messageFormatter = new MessageFormatter();

    private static class ExpectedLogEvent extends ExpectedLogEventPattern {
        public ExpectedLogEvent(String loggerName, Level level, String formattedMessage) {
            super(loggerName, level, formattedMessage);
        }

        public boolean exactMatch(LogEvent event) {
            return event.getLoggerName().equals(loggerName)
                    && event.getLevel() == level
                    && messageFormatter.format(event.getMessage(), event.getArgumentArray()).equals(messagePattern);
        }

        @Override
        public String asString(LogEvent event) {
            return messageFormatter.format(event.getMessage(), event.getArgumentArray());
        }
    }

    private static class ExpectedLogEventWithException extends ExpectedLogEvent {
        private final Throwable expectedException;

        public ExpectedLogEventWithException(String loggerName, Level level, String formattedMessage, Throwable expectedException) {
            super(loggerName, level, formattedMessage);
            this.expectedException = expectedException;
        }

        public boolean exactMatch(LogEvent event) {
            return super.exactMatch(event) &&
                    event.getThrowable() != null &&
                    event.getThrowable().getClass() == expectedException.getClass() &&
                    event.getThrowable().getMessage().equals(expectedException.getMessage());
        }

        @Override
        public String asString(LogEvent event) {
            return super.asString(event) + " with " + event.getThrowable();
        }

        @Override
        public String describe() {
            return super.describe() + " with " + expectedException;
        }
    }

    private Logger logger;
    private List<ExpectedLogEventPattern> filters = new ArrayList<>();
    private List<LogEvent> events = new ArrayList<>();

    @Override
    public void logEvent(LogEvent logEvent) {
        events.add(logEvent);
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
                    verifyCompletion();
                } finally {
                    loggerFactory.setLevel(logger, oldLevel);
                    loggerFactory.setObserver(logger, oldObserver, true);
                }
            }
        };
    }

    public void expectPattern(Class<?> logClass, Level level, String messagePattern) {
        expectPattern(logClass.getName(), level, messagePattern);
    }

    public void expectPattern(String loggerName, Level level, String messagePattern) {
        this.filters.add(new ExpectedLogEventPattern(loggerName, level, messagePattern));
    }

    public void expect(Class<?> logClass, Level level, String formattedMessage) {
        expect(logClass.getName(), level, formattedMessage);
    }

    public void expect(String loggerName, Level level, String formattedMessage) {
        this.filters.add(new ExpectedLogEvent(loggerName, level, formattedMessage));
    }

    public void expect(Class<?> logClass, Level level, String formattedMessage, Throwable expectedException) {
        expect(logClass.getName(), level, formattedMessage, expectedException);
    }

    public void expect(String loggerName, Level level, String formattedMessage, Throwable expectedException) {
        this.filters.add(new ExpectedLogEventWithException(loggerName, level, formattedMessage, expectedException));
    }

    public void verifyCompletion() {
        try {
            Optional<ExpectedLogEventPattern> firstMissedFilter = filters.stream()
                    .filter(filter -> this.events.stream().noneMatch(filter::exactMatch))
                    .findAny();
            firstMissedFilter.ifPresent(filter -> Assert.fail(failureMessage(filter)));

            List<LogEvent> unexpectedEvents = new ArrayList<>(this.events);
            unexpectedEvents.removeIf(event -> event.isBelowThreshold(threshold));
            unexpectedEvents.removeIf(
                    event -> this.filters.stream().anyMatch(filter -> filter.exactMatch(event))
            );
            if (!unexpectedEvents.isEmpty()) {
                Assert.fail("Unexpected events: " + unexpectedEvents.toString());
            }
        } finally {
            filters.clear();
            events.clear();
        }
    }

    private String failureMessage(ExpectedLogEventPattern filter) {
        List<String> applicableMatches = events.stream()
                .filter(filter::partialMatch)
                .filter(event -> !event.isBelowThreshold(threshold))
                .map(event -> filter.asString(event))
                .collect(Collectors.toList());
        if (applicableMatches.isEmpty()) {
            applicableMatches = events.stream()
                    .filter(event -> !event.isBelowThreshold(threshold))
                    .map(event -> filter.asString(event))
                    .collect(Collectors.toList());
        }
        return "Expected message not logged: " + filter.describe()
                +  ". Applicable matches: " + applicableMatches;
    }

}
