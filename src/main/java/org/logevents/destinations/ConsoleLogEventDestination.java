package org.logevents.destinations;

/**
 * Writes output to standard out. A convenience class for configuration purposes.
 */
public class ConsoleLogEventDestination implements LogEventDestination {

    @Override
    public void writeEvent(String logEvent) {
        System.out.print(logEvent);
        System.out.flush();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
