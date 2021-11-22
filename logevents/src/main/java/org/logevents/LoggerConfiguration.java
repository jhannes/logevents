package org.logevents;

 import org.logevents.impl.LogEventFilter;
 import org.logevents.impl.LogEventGenerator;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LocationAwareLogger;

/**
 * The configuration information of a Logger. Used for
 * logic that reports the current configuration.
 *
 * @author Johannes Brodwall
 *
 */
public interface LoggerConfiguration extends Logger, LocationAwareLogger {

    LogEventFilter getOwnFilter();

    /**
     * Calls toString on the observer for this Logger.
     */
    String getObserver();

    LogEventObserver replaceObserver(LogEventObserver observer);

    boolean isConfigured();

    LogEventFilter getEffectiveFilter();

    LogEventGenerator getLogger(Level level);

    LogEventObserver getTraceObservers();

    LogEventObserver getDebugObservers();

    LogEventObserver getInfoObservers();

    LogEventObserver getWarnObservers();

    LogEventObserver getErrorObservers();
}
