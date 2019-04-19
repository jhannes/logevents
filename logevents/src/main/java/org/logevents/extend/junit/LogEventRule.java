package org.logevents.extend.junit;

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
    private List<LogEvent> events = new ArrayList<>();

    public LogEventRule(Level level, String logName) {
        this.level = level;
        this.logger = LoggerFactory.getLogger(logName);
    }

    public LogEventRule(Level level, Class<?> category) {
        this(level, category.getName());
    }

    public void setLevel(Level level) {
        this.level = level;
        if (logger != null) {
            LogEventFactory.getInstance().setLevel(logger, level);
        }
    }

    public void assertNoMessages() {
        assertTrue("Expected no log messages to " + logger + " was " + events,
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

    public String formatMessage(LogEvent event) {
        return new MessageFormatter().format(event.getMessage(), event.getArgumentArray());
    }

    public void assertContainsMessage(Level level, String message, Throwable throwable) {
        assertFalse("Expected <" + message + "> but no messages were logged",
                events.isEmpty());
        for (LogEvent event : events) {
            if (formatMessage(event).equals(message)) {
                assertEquals("Log level for " + message, level, event.getLevel());
                assertEquals("Exception for " + message, throwable, event.getThrowable());
                return;
            }
        }
        fail("Could not find <" + message + "> in logged messages: " + events);
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

                Level oldLevel = loggerFactory.setLevel(logger, level);
                LogEventObserver oldObserver = loggerFactory.setObserver(logger, LogEventRule.this, false);
                try {
                    base.evaluate();
                } finally {
                    loggerFactory.setLevel(logger, oldLevel);
                    loggerFactory.setObserver(logger, oldObserver, true);
                }
            }
        };
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        this.events.add(logEvent);
    }


}
