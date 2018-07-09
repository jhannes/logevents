package org.logevents.destinations;

public class ConsoleLogEventDestination implements LogEventDestination {

    @Override
    public void writeEvent(String logEvent) {
        System.out.print(logEvent);
        System.out.flush();
    }

}
