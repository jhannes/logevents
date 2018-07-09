package org.logevents.destinations;

import java.io.IOException;

public interface LogEventDestination {

    void writeEvent(String format) throws IOException;

}
