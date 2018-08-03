package org.logevents.destinations;

import java.io.IOException;

/**
 * Represents a destination for formatted log messages. Usually
 * a file or console.
 *
 * @author Johannes Brodwall
 *
 */
public interface LogEventDestination {

    void writeEvent(String format) throws IOException;

}
