package org.logevents.observers;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.core.LevelThresholdConditionalObserver;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LevelThresholdConditionalObserverTest {

    private static final List<String> events = new ArrayList<>();

    public static class Observer implements LogEventObserver {
        @Override
        public void logEvent(LogEvent logEvent) {
            events.add(logEvent.getMessage());
        }
    }


    @Test
    public void shouldOutputAtSpecifiedLevels() {
        events.clear();
        Map<String, String> configuration = new HashMap<>();
        configuration.put("observer.level.threshold", Level.WARN.toString());
        configuration.put("observer.level.delegate", Observer.class.getName());
        LevelThresholdConditionalObserver observer = new LevelThresholdConditionalObserver(configuration, "observer.level");

        LogEventFactory factory = LogEventFactory.getInstance();
        Logger logger = factory.getLogger("org.example.LevelThreshold");
        factory.setLevel(logger, Level.TRACE);
        factory.setObserver(logger, observer, false);

        logger.trace("Debug message");
        logger.debug("Debug message");
        logger.info("Info message");
        logger.warn("Warning message");
        logger.error("Error message");

        assertEquals(Arrays.asList("Warning message", "Error message"), events);
    }

}
