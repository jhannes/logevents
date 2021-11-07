package org.logevents.observers;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.logevents.impl.ConditionalLogEventFilter;
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
import static org.logevents.extend.junit.LogEventSampler.PERFORMANCE;

public class ConditionalLogEventFilterTest {

    public LogEventFactory factory = LogEventFactory.getInstance();
    public LoggerConfiguration logger = factory.getLogger("org.example.MdcThreshold");
    public CircularBufferLogEventObserver output = new CircularBufferLogEventObserver();

    @Before
    public void setUp() {
        factory.setLevel(logger, Level.TRACE);
    }

    @Test
    public void shouldLogAtDefaultLevel() {
        factory.setFilter(logger, new ConditionalLogEventFilter(Level.DEBUG));
        factory.setObserver(logger, output, false);

        logger.trace("Excluded");
        logger.debug("Included");

        assertEquals(Arrays.asList("Included"), output.getMessages());
    }

    @Test
    public void shouldOverrideOnSingleMdcValue() {
        ConditionalLogEventFilter filter = new ConditionalLogEventFilter(Level.INFO);
        filter.addLoggingCondition(Level.DEBUG, "mdc:user=admin|super");

        factory.setObserver(logger, output, false);
        factory.setFilter(logger, filter);

        logger.debug("Excluded");
        MDC.put("user", "super");
        logger.debug("Included");
        logger.trace("Excluded");

        assertEquals(Arrays.asList("Included"), output.getMessages());
    }

    @Test
    public void shouldOnlyLogOnceOnMultipleMdcMatches() {
        factory.setFilter(logger, new ConditionalLogEventFilter("WARN,INFO@mdc:user=admin|super,DEBUG@mdc:operation=important"));
        factory.setObserver(logger, output, false);

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
    public void shouldReturnIfLoggingIsTurnedOn() {
        ConditionalLogEventFilter filter = new ConditionalLogEventFilter(Level.INFO);
        filter.addLoggingCondition(Level.DEBUG, "mdc:user=admin|super");
        factory.setFilter(logger, filter);

        MDC.clear();
        assertTrue(logger.isInfoEnabled());
        assertFalse(logger.isDebugEnabled());
        MDC.put("user", "admin");
        assertTrue(logger.isDebugEnabled());
        MDC.put("user", "nadmin");
        assertFalse(logger.isDebugEnabled());
    }

    @Test
    public void shouldSupportMultipleRequiredMdcVariables() {
        factory.setFilter(logger, new ConditionalLogEventFilter("WARN@mdc:user=admin|super&mdc:operation=important"));
        assertEquals(
                "ConditionalLogEventFilter{ERROR=[RequiredMdcCondition{user in [super, admin]} AND RequiredMdcCondition{operation in [important]}],WARN=[RequiredMdcCondition{user in [super, admin]} AND RequiredMdcCondition{operation in [important]}]}",
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
        factory.setFilter(logger, new ConditionalLogEventFilter("ERROR@mdc:user!=admin|super&mdc:operation=important"));
        assertEquals(
                "ConditionalLogEventFilter{ERROR=[SuppressedMdcCondition{user NOT in [super, admin]} AND RequiredMdcCondition{operation in [important]}]}",
                logger.getEffectiveFilter().toString()
        );

        MDC.clear();
        assertFalse(logger.isErrorEnabled());
        MDC.put("operation", "important");
        assertTrue(logger.isErrorEnabled());
        MDC.put("user", "admin");
        assertFalse(logger.isErrorEnabled());
    }

    @Test
    public void shouldSupportMarkerAndMdcFilters() {
        factory.setFilter(logger, new ConditionalLogEventFilter("WARN@mdc:user=admin&marker=HTTP_REQUEST"));
        assertEquals(
                "ConditionalLogEventFilter{ERROR=[RequiredMdcCondition{user in [admin]} AND RequiredMarkerCondition{HTTP_REQUEST}],WARN=[RequiredMdcCondition{user in [admin]} AND RequiredMarkerCondition{HTTP_REQUEST}]}",
                logger.getEffectiveFilter().toString()
        );
        factory.setObserver(logger, output);

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
        factory.setFilter(logger, new ConditionalLogEventFilter("DEBUG@marker=PERFORMANCE"));
        factory.setObserver(logger, output, false);
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
    public void shouldSupportAlternativeMarkers() {
        factory.setFilter(logger, new ConditionalLogEventFilter("DEBUG@marker=HTTP_REQUEST|PERFORMANCE"));
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isDebugEnabled(LIFECYCLE));
        assertTrue(logger.isDebugEnabled(HTTP_REQUEST));
        assertTrue(logger.isDebugEnabled(HTTP_ASSET_REQUEST));
        assertTrue(logger.isDebugEnabled(PERFORMANCE));
    }

    @Test
    public void shouldSupportSuppressedMarkers() {
        factory.setFilter(logger, new ConditionalLogEventFilter("DEBUG@marker!=HTTP_REQUEST|PERFORMANCE"));
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isDebugEnabled(LIFECYCLE));
        assertFalse(logger.isDebugEnabled(HTTP_REQUEST));
        assertFalse(logger.isDebugEnabled(HTTP_ASSET_REQUEST));
        assertFalse(logger.isDebugEnabled(PERFORMANCE));
    }
}
