package org.logevents.optional.junit;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.core.LogEventFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A JUnit @Rule class that can be used to verify that only expected messages are logged.
 *
 * <h2>Example</h2>
 * <pre>
 * public class ExampleTest {
 *    private static final Logger logger = LoggerFactory.getLogger(ExampleTest.class)
 *
 *    &#064;Rule
 *    public ExpectedLogEventsRule expectLogEvents = new ExpectedLogEventsRule(Level.WARN);
 *
 *    &#064;Test
 *    public void willFailBecauseOfUnexpectedLogEvent() {
 *        logger.warn("Whoa there!");
 *    }
 *
 *    &#064;Test
 *    public void willFailBecauseExpectedEventWasNotLogged() {
 *        expectLogEvents.expect(ExampleTest.class, Level.WARN, "Whoa there!");
 *    }
 *
 *    &#064;Test
 *    public void willFailBecauseLogMessageDidNotMatch() {
 *        expectLogEvents.expect(ExampleTest.class, Level.WARN, "Whoa there!");
 *        logger.warn("Another message!");
 *    }
 *
 *    &#064;Test
 *    public void willPass() {
 *        expectLogEvents.expect(ExampleTest.class, Level.WARN, "Whoa there!");
 *        logger.warn("Whoa there!");
 *    }
 *
 *    &#064;Test
 *    public void willFailBecauseTheExceptionClassWasDifferent() {
 *        // The expectations can be highly customized
 *        expectLogEvents.expect(expect -&gt;
 *          expect.level(Level.WARN).logger(ExampleTest.class).pattern("Whoa there!").exception(IOException.class)
 *        );
 *        logger.warn("Whoa there!", new RuntimeException("Uh oh"));
 *    }
 * }
 * </pre>
 */
public class ExpectedLogEventsRule implements TestRule, LogEventObserver {

    protected List<LogEventMatcher> matchers = new ArrayList<>();
    protected List<LogEvent> events = new ArrayList<>();
    private final LogEventFactory loggerFactory;
    private final Level threshold;
    private LogEventObserver fallbackObserver;
    private boolean allowUnexpectedLogs = false;

    public ExpectedLogEventsRule(Level threshold) {
        this(threshold, (LogEventFactory)LoggerFactory.getILoggerFactory());
    }

    ExpectedLogEventsRule(Level threshold, LogEventFactory loggerFactory) {
        this.threshold = threshold;
        this.loggerFactory = loggerFactory;
    }

    public void setAllowUnexpectedLogs(boolean allowUnexpectedLogs){
        this.allowUnexpectedLogs = allowUnexpectedLogs;
    }

    @Override
    public synchronized void logEvent(LogEvent logEvent) {
        events.add(logEvent);
        if (!logEvent.isBelowThreshold(threshold) && matchers.stream().noneMatch(f -> partialMatch(f, logEvent))) {
            fallbackObserver.logEvent(logEvent);
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Logger logger = loggerFactory.getRootLogger();

                LogEventFilter oldFilter = loggerFactory.setLevel(logger, Level.TRACE);
                LogEventObserver oldObserver = loggerFactory.setObserver(logger, ExpectedLogEventsRule.this, false);
                fallbackObserver = oldObserver;
                try {
                    base.evaluate();
                    verifyCompletion();
                } finally {
                    loggerFactory.setFilter(logger, oldFilter);
                    loggerFactory.setObserver(logger, oldObserver, true);
                }
            }
        };
    }

    public void expectMatch(LogEventMatcher matcher) {
        this.matchers.add(matcher);
    }

    public void expectPattern(Class<?> logClass, Level level, String messagePattern) {
        expectPattern(logClass.getName(), level, messagePattern);
    }

    public void expectPattern(String loggerName, Level level, String messagePattern) {
        expectMatch(expect -> expect.level(level).logger(loggerName).pattern(messagePattern));
    }

    public void expect(Class<?> logClass, Level level, String formattedMessage) {
        expect(logClass.getName(), level, formattedMessage);
    }

    public synchronized void expect(String loggerName, Level level, String formattedMessage) {
        expectMatch(expect -> expect.level(level).logger(loggerName).formattedMessage(formattedMessage));
    }

    public void expect(Class<?> logClass, Level level, String formattedMessage, Throwable expectedException) {
        expect(logClass.getName(), level, formattedMessage, expectedException);
    }

    public synchronized void expect(String loggerName, Level level, String formattedMessage, Throwable expectedException) {
        expectMatch(expect -> expect.level(level).logger(loggerName).formattedMessage(formattedMessage)
                        .exception(expectedException.getClass(), expectedException.getMessage())
        );
    }

    public synchronized void verifyCompletion() {
        try {
            verifyAllExpectedLogsArePresent();

            if(!allowUnexpectedLogs){
                verifyNoUnexpectedLogsArePresent();
            }
        } finally {
            matchers.clear();
            events.clear();
        }
    }

    private void verifyAllExpectedLogsArePresent() {
        Optional<LogEventMatcher> firstMissedMatcher = matchers.stream()
                .filter(m -> this.events.stream().noneMatch(e -> exactMatch(m, e)))
                .findFirst();
        firstMissedMatcher.ifPresent(matcher -> Assert.fail(failureMessage(matcher)));
    }

    private void verifyNoUnexpectedLogsArePresent() {
        List<LogEvent> unexpectedEvents = new ArrayList<>(this.events);
        unexpectedEvents.removeIf(event -> event.isBelowThreshold(threshold));
        unexpectedEvents.removeIf(
                event -> this.matchers.stream().anyMatch(filter -> exactMatch(filter, event))
        );
        if (!unexpectedEvents.isEmpty()) {
            Assert.fail("Unexpected log message: " + unexpectedEvents);
        }
    }

    private boolean partialMatch(LogEventMatcher matcher, LogEvent event) {
        return new LogEventMatcherContext(event, matcher).isPartialMatch();
    }

    private boolean exactMatch(LogEventMatcher matcher, LogEvent event) {
        return new LogEventMatcherContext(event, matcher).isExactMatch();
    }

    private String failureMessage(LogEventMatcher matcher) {
        List<LogEventMatcherContext> applicableMatches = events.stream()
                .map(e -> new LogEventMatcherContext(e, matcher))
                .filter(LogEventMatcherContext::isPartialMatch)
                .collect(Collectors.toList());
        if (applicableMatches.isEmpty()) {
            applicableMatches = events.stream()
                    .map(e -> new LogEventMatcherContext(e, matcher))
                    .filter(m -> m.getMatchedFields().contains("logger"))
                    .collect(Collectors.toList());
        }
        if (applicableMatches.isEmpty()) {
            applicableMatches = events.stream()
                    .filter(event -> !event.isBelowThreshold(threshold))
                    .map(e -> new LogEventMatcherContext(e, matcher))
                    .collect(Collectors.toList());
        }
        if (applicableMatches.isEmpty()) {
            LogEvent dummyEvent = new LogEvent(null, null, null, null, new Object[0]);
            return "Nothing logged. Expected " + new LogEventMatcherContext(dummyEvent, matcher).diff();
        }

        return "Expected message not logged: " + applicableMatches.get(0).describePartialMatch()
                + ". Close matches: " + applicableMatches.stream().map(LogEventMatcherContext::diff).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return "ExpectedLogEventsRule{matchers=" + matchers.size() + ", events=" + new ArrayList<>(events) + '}';
    }
}
