package org.logevents.observers;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.logevents.config.Configuration;
import org.logevents.extend.junit.LogEventRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Properties;

import static org.logevents.extend.junit.LogEventSampler.HTTP_ERROR;
import static org.logevents.extend.junit.LogEventSampler.HTTP_REQUEST;
import static org.logevents.extend.junit.LogEventSampler.HTTP_ASSET_REQUEST;
import static org.logevents.extend.junit.LogEventSampler.LIFECYCLE;
import static org.logevents.extend.junit.LogEventSampler.OPS;

public class FilteredLogEventObserverTest {

    private LogEventRule logEventRule = new LogEventRule(Level.INFO, FilteredLogEventObserverTest.class);

    private static final Logger logger = LoggerFactory.getLogger(FilteredLogEventObserverTest.class);
    private AbstractFilteredLogEventObserver observer = new AbstractFilteredLogEventObserver() {
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

    @Test
    public void shouldSuppressUnwantedMarkers() {
        observer.setSuppressMarkers(Arrays.asList(HTTP_REQUEST));

        logger.warn(HTTP_REQUEST, "Don't log my http requests data");
        logger.warn(HTTP_ASSET_REQUEST, "Don't log markers that contain the marker");
        logger.warn("Log if no markers");
        logger.warn(OPS, "Log if unmentioned markers");

        logEventRule.assertDoesNotContainMessage("Don't log my personal data");
        logEventRule.assertDoesNotContainMessage("Audit should not be logged");
        logEventRule.assertContainsMessage(Level.WARN, "Log if unmentioned markers");
        logEventRule.assertContainsMessage(Level.WARN, "Log if no markers");
    }

    @Test
    public void shouldRequireDesiredMarkers() {
        observer.setRequireMarker(Arrays.asList(HTTP_REQUEST));

        logger.warn(LIFECYCLE, "Don't log my unrelated markers");
        logger.warn("Don't log if no markers");

        logger.warn(HTTP_REQUEST, "Log if mentioned markers");
        logger.warn(HTTP_ERROR, "Log if contained markers");

        logEventRule.assertDoesNotContainMessage("Don't log my unrelated markers");
        logEventRule.assertDoesNotContainMessage("Don't log if no markers");
        logEventRule.assertContainsMessage(Level.WARN, "Log if mentioned markers");
        logEventRule.assertContainsMessage(Level.WARN, "Log if contained markers");
    }
    
    
    @Test
    public void shouldRequireMdcValues() {
        Properties properties = new Properties();
        properties.put("observer.foo.requireMdc.user", "tester1|tester2");
        properties.put("observer.foo.requireMdc.requestPath", "/test/target|/index.html");
        Configuration configuration = new Configuration(properties, "observer.foo");
        observer.configureFilter(configuration, Level.TRACE);
        configuration.checkForUnknownFields();
        LogEventFactory.getInstance().setObserver((LoggerConfiguration) logger, observer);

        MDC.clear();
        logger.info("Excluded - no MDC");
        MDC.put("user", "admin");
        MDC.put("requestPath", "/status");
        logger.info("Excluded - wrong MDC");
        MDC.put("user", "tester1");
        logger.info("Excluded - partial MDC");
        MDC.put("requestPath", "/test/target");
        logger.info("Included");
        
        logEventRule.assertDoesNotContainMessage("Excluded - no MDC");
        logEventRule.assertDoesNotContainMessage("Excluded - wrong MDC");
        logEventRule.assertDoesNotContainMessage("Excluded - partial MDC");
        logEventRule.assertContainsMessage(Level.INFO, "Included");
    }
    
    @Test
    public void shouldSuppressMdcValues() {
        Properties properties = new Properties();
        properties.put("observer.foo.suppressMdc.user", "admin|operator");
        properties.put("observer.foo.suppressMdc.privileged", "true");
        properties.put("observer.foo.requireMdc.requestPath", "/test/target|/index.html");
        Configuration configuration = new Configuration(properties, "observer.foo");
        observer.configureFilter(configuration, Level.TRACE);
        configuration.checkForUnknownFields();
        LogEventFactory.getInstance().setObserver((LoggerConfiguration) logger, observer);

        MDC.clear();
        MDC.put("requestPath", "/test/target");
        logger.info("Included");
        MDC.put("user", "admin");
        logger.info("Excluded - wrong MDC user");
        MDC.put("user", "tester1");
        logger.info("Included - non-suppressed MDC");
        MDC.put("privileged", "true");
        logger.info("Excluded - wrong MDC privileged");
        
        logEventRule.assertDoesNotContainMessage("Excluded - wrong MDC user");
        logEventRule.assertDoesNotContainMessage("Excluded - wrong MDC privileged");
        logEventRule.assertContainsMessage(Level.INFO, "Included");
        logEventRule.assertContainsMessage(Level.INFO, "Included - non-suppressed MDC");
    }
    

}
