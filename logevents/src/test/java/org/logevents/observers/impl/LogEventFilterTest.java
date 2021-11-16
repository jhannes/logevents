package org.logevents.observers.impl;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.logevents.impl.LogEventFilter;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.logevents.extend.junit.LogEventSampler.HTTP_ASSET_REQUEST;
import static org.logevents.extend.junit.LogEventSampler.HTTP_ERROR;
import static org.logevents.extend.junit.LogEventSampler.HTTP_REQUEST;
import static org.logevents.extend.junit.LogEventSampler.LIFECYCLE;
import static org.logevents.extend.junit.LogEventSampler.OPS;
import static org.logevents.extend.junit.LogEventSampler.PERFORMANCE;

public class LogEventFilterTest {

    public LogEventFactory factory = LogEventFactory.getInstance();
    public LoggerConfiguration parentLogger = factory.getLogger("org.example");
    public LoggerConfiguration logger = factory.getLogger("org.example.MdcThreshold");
    public CircularBufferLogEventObserver output = new CircularBufferLogEventObserver();

    @Before
    public void setUp() {
        factory.setLevel(parentLogger, Level.ERROR);
        factory.setLevel(logger, Level.TRACE);
        factory.setObserver(logger, output, false);
    }

    @Test
    public void shouldLogAtDefaultLevel() {
        factory.setFilter(logger, new LogEventFilter(Level.DEBUG));

        logger.trace("Excluded");
        logger.debug("Included");

        assertEquals(Arrays.asList("Included"), output.getMessages());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowOnUnknownRule() {
        new LogEventFilter("WARN@garbage");
    }

    @Test
    public void shouldSupportRequiredMdcVariables() {
        factory.setFilter(logger, new LogEventFilter("WARN@mdc:user=admin|super&mdc:operation=important"));
        assertEquals(
                "LogEventFilter{ERROR,WARN=RequiredMdcCondition{user in [super, admin]} AND RequiredMdcCondition{operation in [important]}}",
                logger.getEffectiveFilter().toString()
        );

        MDC.clear();
        assertFalse(logger.isWarnEnabled());
        MDC.put("user", "admin");
        assertFalse(logger.isWarnEnabled());
        MDC.put("operation", "important");
        assertTrue(logger.isWarnEnabled());
    }

    @Test
    public void shouldSupportSuppressedMdcVariables() {
        factory.setFilter(parentLogger, LogEventFilter.never());
        factory.setFilter(logger, new LogEventFilter("ERROR@mdc:user!=admin|super&mdc:operation=important"));
        assertEquals(
                "LogEventFilter{ERROR=SuppressedMdcCondition{user NOT in [super, admin]} AND RequiredMdcCondition{operation in [important]}}",
                logger.getEffectiveFilter().toString()
        );

        MDC.clear();
        assertFalse(logger.isErrorEnabled());
        MDC.put("operation", "important");
        assertTrue(logger.isErrorEnabled());
        assertFalse(logger.isWarnEnabled());
        MDC.put("user", "admin");
        assertFalse(logger.isErrorEnabled());
    }

    @Test
    public void shouldOnlyLogOnceOnMultipleMdcMatches() {
        factory.setFilter(logger, new LogEventFilter("WARN,INFO@mdc:user=admin|super,DEBUG@mdc:operation=important"));

        MDC.clear();
        logger.debug("Excluded");
        MDC.put("user", "super");
        logger.info("Included 1");
        logger.debug("Excluded");
        MDC.put("operation", "important");
        logger.debug("Included 2");
        logger.info("Included 3");

        assertEquals(Arrays.asList("Included 1", "Included 2", "Included 3"), output.getMessages());
    }

    @Test
    public void shouldLowerLogLevelWithMdc() {
        factory.setFilter(logger, new LogEventFilter("INFO,WARN@mdc:user=noisy&mdc:operation=boring"));
        assertEquals(
                "LogEventFilter{ERROR,WARN,INFO=(NOT RequiredMdcCondition{user in [noisy]} AND RequiredMdcCondition{operation in [boring]})}",
                logger.getEffectiveFilter().toString()
        );

        MDC.clear();
        assertTrue(logger.isInfoEnabled());
        MDC.put("user", "noisy");
        assertTrue(logger.isInfoEnabled());
        MDC.put("operation", "boring");
        assertFalse(logger.isInfoEnabled());
        MDC.put("user", "normal");
        assertTrue(logger.isInfoEnabled());
    }

    @Test
    public void shouldReturnIfLoggingIsTurnedOn() {
        factory.setFilter(logger, new LogEventFilter("INFO,DEBUG@mdc:user=admin|super"));
        assertEquals(
                "LogEventFilter{ERROR,WARN,INFO,DEBUG=RequiredMdcCondition{user in [super, admin]}}",
                logger.getEffectiveFilter().toString()
        );

        MDC.clear();
        assertTrue(logger.toString(), logger.isInfoEnabled());
        assertFalse(logger.isDebugEnabled());
        MDC.put("user", "admin");
        assertTrue(logger.isDebugEnabled());
        MDC.put("user", "notadmin");
        assertFalse(logger.isDebugEnabled());
    }

    @Test
    public void shouldSupportMarkerAndMdcFilters() {
        factory.setFilter(parentLogger, new LogEventFilter("NONE"));
        factory.setFilter(logger, new LogEventFilter("WARN@mdc:user=admin&marker=HTTP_REQUEST"));
        assertEquals(
                "LogEventFilter{ERROR=RequiredMdcCondition{user in [admin]} AND RequiredMarkerCondition{HTTP_REQUEST},WARN=RequiredMdcCondition{user in [admin]} AND RequiredMarkerCondition{HTTP_REQUEST}}",
                logger.getEffectiveFilter().toString()
        );

        MDC.clear();
        assertFalse(logger.isWarnEnabled());
        assertFalse(logger.isWarnEnabled(HTTP_ERROR));
        logger.warn("Excluded - no MDC");
        logger.warn(HTTP_ERROR, "Excluded - no MDC");
        MDC.put("user", "admin");
        assertFalse(logger.isWarnEnabled());
        assertFalse(logger.isWarnEnabled(LIFECYCLE));
        assertTrue(logger.isWarnEnabled(HTTP_ERROR));
        logger.warn("Excluded - no marker");
        logger.warn(LIFECYCLE, "Excluded - wrong marker");
        logger.warn(HTTP_ERROR, "Included");
        assertEquals(Arrays.asList("Included"), output.getMessages());
    }

    @Test
    public void shouldLogWithMarkers() {
        factory.setFilter(logger, new LogEventFilter("DEBUG@marker=PERFORMANCE"));
        logger.debug(PERFORMANCE, "With no args");
        logger.debug(PERFORMANCE, "With one arg={}", "one");
        logger.debug(PERFORMANCE, "With two args: {} and {}", "one", "two");
        logger.debug(PERFORMANCE, "With more args: {}, {} and {}", "one", "two", "three");
        logger.debug(PERFORMANCE, "With exception", new Exception());

        List<String> expected = Arrays.asList(
                "With no args",
                "With one arg={}",
                "With two args: {} and {}",
                "With more args: {}, {} and {}",
                "With exception"
        );
        assertEquals(expected, output.getMessages());
    }

    @Test
    public void shouldReduceLevelForConditions() {
        factory.setFilter(logger, new LogEventFilter("DEBUG,INFO@marker=PERFORMANCE,WARN@marker=HTTP_REQUEST"));
        assertEquals(
                "LogEventFilter{ERROR,WARN," +
                    "INFO=SuppressedMarkerCondition{HTTP_REQUEST}," +
                    "DEBUG=SuppressedMarkerCondition{PERFORMANCE|HTTP_REQUEST}}",
                logger.getOwnFilter().toString()
        );
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isDebugEnabled(LIFECYCLE));
        assertFalse(logger.isDebugEnabled(PERFORMANCE));
        assertTrue(logger.isInfoEnabled(PERFORMANCE));
        assertFalse(logger.isInfoEnabled(HTTP_REQUEST));
    }

    @Test
    public void shouldSupportSuppressedMarkers() {
        factory.setFilter(logger, new LogEventFilter("DEBUG@marker!=HTTP_REQUEST,DEBUG@marker!=PERFORMANCE,DEBUG@mdc:foo=bar"));
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isDebugEnabled(LIFECYCLE));
        assertFalse(logger.isDebugEnabled(HTTP_REQUEST));
        assertFalse(logger.isDebugEnabled(HTTP_ASSET_REQUEST));
        assertFalse(logger.isDebugEnabled(PERFORMANCE));
    }

    @Test
    public void shouldCombineFilterWithParent() {
        Logger parentLogger = LogEventFactory.getInstance().getLogger("org.example");
        factory.setFilter(parentLogger, new LogEventFilter(Level.WARN));
        factory.setFilter(logger, new LogEventFilter("ERROR@marker=PERFORMANCE,INFO@marker=OPS"));
        assertEquals("LogEventFilter{ERROR=Inherit OR RequiredMarkerCondition{PERFORMANCE} OR RequiredMarkerCondition{OPS},WARN=Inherit AND SuppressedMarkerCondition{PERFORMANCE} OR RequiredMarkerCondition{OPS},INFO=Inherit AND SuppressedMarkerCondition{PERFORMANCE} OR RequiredMarkerCondition{OPS},DEBUG=Inherit AND SuppressedMarkerCondition{PERFORMANCE} AND SuppressedMarkerCondition{OPS},TRACE=Inherit AND SuppressedMarkerCondition{PERFORMANCE} AND SuppressedMarkerCondition{OPS}}", logger.getOwnFilter().toString());
        assertEquals("LogEventFilter{ERROR,WARN=SuppressedMarkerCondition{PERFORMANCE},INFO=RequiredMarkerCondition{OPS}}", logger.getEffectiveFilter().toString());
        assertTrue(logger.isInfoEnabled(OPS));
        assertTrue(logger.isWarnEnabled());
        assertFalse(logger.isWarnEnabled(PERFORMANCE));

        LoggerConfiguration childLogger = factory.getLogger("org.example.MdcThreshold.Sublogger");
        factory.setFilter(childLogger, new LogEventFilter("NONE@marker=PERFORMANCE"));
        assertEquals("LogEventFilter{ERROR=SuppressedMarkerCondition{PERFORMANCE},WARN=SuppressedMarkerCondition{PERFORMANCE},INFO=RequiredMarkerCondition{OPS}}", childLogger.getEffectiveFilter().toString());
        assertFalse(childLogger.isErrorEnabled(PERFORMANCE));
        assertFalse(logger.isInfoEnabled());
        assertTrue(logger.isInfoEnabled(OPS));
    }
}
