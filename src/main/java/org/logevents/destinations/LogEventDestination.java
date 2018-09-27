package org.logevents.destinations;

/**
 * Represents a destination for formatted log messages. Usually
 * a file or console.
 *
 * @author Johannes Brodwall
 *
 */
public interface LogEventDestination {

    void writeEvent(String format);

}
