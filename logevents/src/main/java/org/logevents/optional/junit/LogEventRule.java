package org.logevents.optional.junit;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.core.LogEventFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LogEventRule implements TestRule, LogEventObserver {

    private final Logger logger;
    private Level level;
    private final List<LogEvent> events = new ArrayList<>();

    public LogEventRule(Level level, Logger logger) {
        this.level = level;
        this.logger = logger;
    }

    public LogEventRule(Level level, String logName) {
        this(level, LoggerFactory.getLogger(logName));
    }

    public LogEventRule(Level level, Class<?> category) {
        this(level, LoggerFactory.getLogger(category));
    }

    public void setLevel(Level level) {
        this.level = level;
        if (logger != null) {
            LogEventFactory.getInstance().setLevel(logger, level);
        }
    }

    public void assertNoMessages() {
        assertTrue("Expected no log messages to " + logger.getName() + " was " + events,
                events.isEmpty());
    }

    public void assertNoMessages(Level level) {
        List<LogEvent> events = this.events.stream()
                .filter(m -> !m.isBelowThreshold(level))
                .collect(Collectors.toList());
        assertTrue("Expected no log messages to " + logger.getName() + " at level " + level + " was " + events,
                events.isEmpty());
    }


    public void assertSingleMessage(Level level, String message) {
        List<LogEvent> events = this.events.stream()
                .filter(m -> m.getLevel().equals(level))
                .collect(Collectors.toList());
        assertTrue("Expected only one logged message at level " + level + ", but was " + events,
                events.size() == 1);
        assertEquals(message, formatMessage(events.get(0)));
        assertEquals(level, events.get(0).getLevel());
    }

    public void assertContainsMessage(Level level, String message) {
        assertFalse("Expected <" + message + "> but no messages were logged",
                events.isEmpty());
        for (LogEvent event : events) {
            if (formatMessage(event).equals(message)) {
                assertEquals("Log level for " + message, level, event.getLevel());
                return;
            }
        }
        fail("Could not find <" + message + "> in logged messages: " + events);
    }

    public void assertContainsMessagePattern(Level level, String message) {
        assertFalse("Expected <" + message + "> but no messages were logged",
                events.isEmpty());
        for (LogEvent event : events) {
            if (event.getMessage().equals(message)) {
                assertEquals("Log level for " + message, level, event.getLevel());
                return;
            }
        }
        fail("Could not find <" + message + "> in logged messages: " + events);
    }

    public String formatMessage(LogEvent event) {
        return event.getMessage(new MessageFormatter());
    }

    public void assertContainsMessage(Level level, String message, Throwable throwable) {
        assertFalse("Expected <" + message + "> but no messages were logged",
                events.isEmpty());
        List<String> messages = new ArrayList<>();
        for (LogEvent event : events) {
            String logMessage = formatMessage(event);
            if (logMessage.equals(message)) {
                assertEquals("Log level for " + message, level, event.getLevel());
                assertExceptionEquals(message, throwable, event);
                return;
            }
            messages.add(logMessage);
        }
        fail("Could not find <" + message + "> in logged messages: " + messages);
    }

    void assertExceptionEquals(String message, Throwable throwable, LogEvent event) {
        assertEquals("Exception for " + message, throwable.getClass(), event.getThrowable().getClass());
        assertEquals("Exception for " + message, throwable.getMessage(), event.getThrowable().getMessage());
    }

    public void assertDoesNotContainMessage(String message) {
        for (LogEvent event : events) {
            if (formatMessage(event).equals(message)) {
                fail("Did not expect to find find <" + message + "> in logged messages, but was " + event);
            }
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {


            @Override
            public void evaluate() throws Throwable {
                LogEventFactory loggerFactory = (LogEventFactory) LoggerFactory.getILoggerFactory();

                LogEventFilter oldFilter = loggerFactory.setLevel(logger, level);
                LogEventObserver oldObserver = loggerFactory.setObserver(logger, LogEventRule.this, false);
                try {
                    base.evaluate();
                } finally {
                    loggerFactory.setFilter(logger, oldFilter);
                    loggerFactory.setObserver(logger, oldObserver, true);
                }
            }
        };
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        this.events.add(logEvent);
    }

    public void clear() {
        this.events.clear();
    }
}
