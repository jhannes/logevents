package org.logevents.observers;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.extend.junit.LogEventRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

public class FilteredLogEventObserverTest {

    private LogEventRule logEventRule = new LogEventRule(Level.INFO, FilteredLogEventObserverTest.class);

    private static final Logger logger = LoggerFactory.getLogger(FilteredLogEventObserverTest.class);
    private FilteredLogEventObserver observer = new FilteredLogEventObserver() {
        @Override
        protected void doLogEvent(LogEvent logEvent) {
            logEventRule.logEvent(logEvent);
        }
    };

    @Before
    public void setupLogger() {
        LogEventFactory.getInstance().setLevel(logger, Level.DEBUG);
        LogEventFactory.getInstance().setObserver(logger, observer, false);
    }

    @Test
    public void shouldSuppressLowerLevel() {
        observer.setThreshold(Level.WARN);

        logger.error("Error");
        logger.info("Suppressed");

        logEventRule.assertContainsMessage(Level.ERROR, "Error");
        logEventRule.assertDoesNotContainMessage("Suppressed");
    }

    private static final Marker PERSONAL_DATA = MarkerFactory.getMarker("PERSONAL_DATA");
    private static final Marker SECURITY = MarkerFactory.getMarker("SECURITY");
    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    static {
        SECURITY.add(AUDIT);
        PERSONAL_DATA.add(AUDIT);
    }

    @Test
    public void shouldSuppressUnwantedMarkers() {
        observer.setSuppressMarkers(new String[] { PERSONAL_DATA.getName() });

        logger.warn(PERSONAL_DATA, "Don't log my personal data");
        logger.warn(AUDIT, "Audit should not be logged");
        logger.warn("Log if no markers");
        logger.warn(SECURITY, "Log if unmentioned markers");

        logEventRule.assertDoesNotContainMessage("Don't log my personal data");
        logEventRule.assertDoesNotContainMessage("Audit should not be logged");
        logEventRule.assertContainsMessage(Level.WARN, "Log if unmentioned markers");
        logEventRule.assertContainsMessage(Level.WARN, "Log if no markers");
    }

    @Test
    public void shouldRequestDesiredMarkers() {
        observer.setRequireMarker(new String[] { SECURITY.getName() });

        logger.warn(PERSONAL_DATA, "Don't log my unrelated markers");
        logger.warn("Don't log if no markers");

        logger.warn(SECURITY, "Log if mentioned markers");
        logger.warn(AUDIT, "Log if contained markers");

        logEventRule.assertDoesNotContainMessage("Don't log my unrelated markers");
        logEventRule.assertDoesNotContainMessage("Don't log if no markers");
        logEventRule.assertContainsMessage(Level.WARN, "Log if mentioned markers");
        logEventRule.assertContainsMessage(Level.WARN, "Log if contained markers");
    }

}