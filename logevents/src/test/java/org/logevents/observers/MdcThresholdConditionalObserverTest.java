package org.logevents.observers;

import org.junit.Before;
import org.junit.Test;
import org.logevents.LogEventFactory;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class MdcThresholdConditionalObserverTest {

    public LogEventFactory factory = LogEventFactory.getInstance();
    public Logger logger = factory.getLogger("org.example.MdcThreshold");
    public CircularBufferLogEventObserver output = new CircularBufferLogEventObserver();
    
    @Before
    public void setUp() {
        factory.setLevel(logger, Level.TRACE);
    }

    @Test
    public void shouldLogAtDefaultLevel() {
        MdcThresholdConditionalObserver observer = new MdcThresholdConditionalObserver(output, Level.DEBUG);

        factory.setObserver(logger, observer, false);

        logger.trace("Excluded");
        logger.debug("Included");
        
        assertEquals(Arrays.asList("Included"), output.getMessages());
    }
    
    @Test
    public void shouldOverrideOnSingleMdcValue() {
        MdcThresholdConditionalObserver observer = new MdcThresholdConditionalObserver(output, Level.INFO);
        observer.addMdcFilter(Level.DEBUG, "user", Arrays.asList("admin", "super"));

        factory.setObserver(logger, observer, false);

        logger.debug("Excluded");
        MDC.put("user", "super");
        logger.debug("Included");
        logger.trace("Excluded");

        assertEquals(Arrays.asList("Included"), output.getMessages());
    }
    
    @Test
    public void shouldOnlyLogOnceOnMultipleMdcMatches() {
        MdcThresholdConditionalObserver observer = new MdcThresholdConditionalObserver("WARN,INFO@mdc:user=admin|super,DEBUG@mdc:operation=important", output);

        factory.setObserver(logger, observer, false);

        logger.debug("Excluded");
        MDC.put("user", "super");
        logger.info("Included 1");
        logger.debug("Excluded");
        MDC.put("operation", "important");
        logger.debug("Included 2");
        logger.info("Included 3");

        assertEquals(Arrays.asList("Included 1", "Included 2", "Included 3"), output.getMessages());
    }
    
}
