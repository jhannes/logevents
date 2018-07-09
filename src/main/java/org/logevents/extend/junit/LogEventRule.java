package org.logevents.extend.junit;

import java.util.ArrayList;
import java.util.List;

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
    private Level level;
    private List<LogEvent> events = new ArrayList<>();

    public LogEventRule(String logName, Level level) {
        this.logName = logName;
        this.level = level;
    }

    public void assertSingleMessage(String message, Level level) {
        assertEquals(1, events.size());
        assertEquals(message, events.get(0).getMessage());
        assertEquals(level, events.get(0).getLevel());
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                LogEventFactory loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory();
                Logger logger = loggerFactory.getLogger(logName);

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
