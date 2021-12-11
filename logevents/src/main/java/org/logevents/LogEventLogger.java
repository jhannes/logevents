package org.logevents;

 import org.logevents.core.LogEventFilter;
 import org.logevents.core.LogEventGenerator;
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
public interface LogEventLogger extends Logger, LocationAwareLogger {

    LogEventFilter getOwnFilter();

    /**
     * Calls toString on the observer for this Logger.
     */
    String getObserver();

    LogEventObserver replaceObserver(LogEventObserver observer);

    /**
     * Returns true if this logger has configuration separately from its parent
     */
    boolean isConfigured();

    LogEventFilter getEffectiveFilter();

    LogEventGenerator getLogger(Level level);

    LogEventObserver getTraceObservers();

    LogEventObserver getDebugObservers();

    LogEventObserver getInfoObservers();

    LogEventObserver getWarnObservers();

    LogEventObserver getErrorObservers();
}
