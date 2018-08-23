package org.logevents.extend.junit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.impl.StaticLoggerBinder;

import static org.junit.Assert.*;

public class LogEventRule implements TestRule, LogEventObserver {

    private String logName;
    private Logger logger;
    private Level level;
    private List<LogEvent> events = new ArrayList<>();

    public LogEventRule(Level level, String logName) {
        this.logName = logName;
        this.level = level;
    }

    public void setLevel(Level level) {
        this.level = level;
        if (logger != null) {
            LogEventFactory.getInstance().setLevel(logger, level);
        }
    }

    public void assertNoMessages() {
        assertTrue("Expected no log messages to " + logName + " was " + events,
                events.isEmpty());
    }

    public void assertSingleMessage(Level level, String message) {
        List<LogEvent> events = this.events.stream()
            .filter(m -> m.getLevel().equals(level))
            .collect(Collectors.toList());
        assertTrue("Expected only one logged message at level " + level + ", but was " + events,
                events.size() == 1);
        assertEquals(message, events.get(0).formatMessage());
        assertEquals(level, events.get(0).getLevel());
    }

    public void assertContainsMessage(Level level, String message) {
        assertFalse("Expected <" + message + "> but no messages were logged",
                events.isEmpty());
        for (LogEvent event : events) {
            if (event.formatMessage().equals(message)) {
                assertEquals("Log level for " + message, level, event.getLevel());
                return;
            }
        }
        fail("Could not find <" + message + "> in logged messages: " + events);
    }

    public void assertDoesNotContainMessage(String message) {
        for (LogEvent event : events) {
            if (event.formatMessage().equals(message)) {
                fail("Did not expect to find find <" + message + "> in logged messages, but was " + event);
            }
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {


            @Override
            public void evaluate() throws Throwable {
                LogEventFactory loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory();
                logger = loggerFactory.getLogger(logName);

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
