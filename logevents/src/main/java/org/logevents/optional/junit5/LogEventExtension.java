package org.logevents.optional.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.core.LogEventFilter;
import org.logevents.formatters.messages.MessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class LogEventExtension implements BeforeEachCallback, AfterEachCallback, LogEventObserver {

    private final Logger logger;
    private Level level;
    private final List<LogEvent> events = new ArrayList<>();
    private LogEventFactory loggerFactory;
    private LogEventFilter oldFilter;
    private LogEventObserver oldObserver;

    public LogEventExtension(Level level, Logger logger) {
        this.level = level;
        this.logger = logger;
    }

    public LogEventExtension(Level level, String logName) {
        this(level, LoggerFactory.getLogger(logName));
    }

    public LogEventExtension(Level level, Class<?> category) {
        this(level, LoggerFactory.getLogger(category));
    }

    public void setLevel(Level level) {
        this.level = level;
        if (logger != null) {
            LogEventFactory.getInstance().setLevel(logger, level);
        }
    }

    public void assertNoMessages() {
        assertTrue(events.isEmpty(), () -> "Expected no log messages to " + logger.getName() + " was " + events);
    }

    public void assertNoMessages(Level level) {
        List<LogEvent> events = this.events.stream()
                .filter(m -> !m.isBelowThreshold(level))
                .collect(Collectors.toList());
        assertTrue(events.isEmpty(),
            () -> "Expected no log messages to " + logger.getName() + " at level " + level + " was " + events);
    }


    public void assertSingleMessage(Level level, String message) {
        List<LogEvent> events = this.events.stream()
                .filter(m -> m.getLevel().equals(level))
                .collect(Collectors.toList());
        assertEquals(1, events.size(),
            () -> "Expected only one logged message at level " + level + ", but was " + events);
        assertEquals(message, formatMessage(events.get(0)));
        assertEquals(level, events.get(0).getLevel());
    }

    public void assertContainsMessage(Level level, String message) {
        assertFalse(events.isEmpty(), () -> "Expected <" + message + "> but no messages were logged");
        for (LogEvent event : events) {
            if (formatMessage(event).equals(message)) {
                assertEquals(level, event.getLevel(), () -> "Log level for " + message);
                return;
            }
        }
        fail("Could not find <" + message + "> in logged messages: " + events);
    }

    public void assertContainsMessagePattern(Level level, String message) {
        assertFalse(events.isEmpty(), () -> "Expected <" + message + "> but no messages were logged");
        for (LogEvent event : events) {
            if (event.getMessage().equals(message)) {
                assertEquals(level, event.getLevel(), () -> "Log level for " + message);
                return;
            }
        }
        fail("Could not find <" + message + "> in logged messages: " + events);
    }

    public String formatMessage(LogEvent event) {
        return event.getMessage(new MessageFormatter());
    }

    public void assertContainsMessage(Level level, String message, Throwable throwable) {
        assertFalse(events.isEmpty(), () -> "Expected <" + message + "> but no messages were logged");
        List<String> messages = new ArrayList<>();
        for (LogEvent event : events) {
            String logMessage = formatMessage(event);
            if (logMessage.equals(message)) {
                assertEquals(level, event.getLevel(), () -> "Log level for " + message);
                assertExceptionEquals(message, throwable, event);
                return;
            }
            messages.add(logMessage);
        }
        fail("Could not find <" + message + "> in logged messages: " + messages);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        loggerFactory = (LogEventFactory) LoggerFactory.getILoggerFactory();

        oldFilter = loggerFactory.setLevel(logger, level);
        oldObserver = loggerFactory.setObserver(logger, LogEventExtension.this, false);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        loggerFactory.setFilter(logger, oldFilter);
        loggerFactory.setObserver(logger, oldObserver, true);
    }

    void assertExceptionEquals(String message, Throwable throwable, LogEvent event) {
        assertEquals(throwable.getClass(), event.getThrowable().getClass(), () -> "Exception for " + message);
        assertEquals(throwable.getMessage(), event.getThrowable().getMessage(), () -> "Exception for " + message);
    }

    public void assertDoesNotContainMessage(String message) {
        for (LogEvent event : events) {
            if (formatMessage(event).equals(message)) {
                fail("Did not expect to find find <" + message + "> in logged messages, but was " + event);
            }
        }
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        this.events.add(logEvent);
    }

    public void clear() {
        this.events.clear();
    }
}
