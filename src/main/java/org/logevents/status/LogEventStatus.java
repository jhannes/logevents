package org.logevents.status;

import java.util.ArrayList;
import java.util.List;

import org.logevents.status.StatusEvent.Level;
import org.logevents.util.CircularBuffer;

public class LogEventStatus {

    private static LogEventStatus instance = new LogEventStatus();

    public static LogEventStatus getInstance() {
        return instance;
    }

    private List<StatusEvent> headMessages = new ArrayList<>();
    private CircularBuffer<StatusEvent> tailMessages = new CircularBuffer<>();
    private StatusEvent.Level threshold = Level.valueOf(System.getProperty("logevents.status", Level.ERROR.toString()));

    public void addFatal(Object location, String message, Throwable throwable) {
        add(new StatusEvent(location, message, StatusEvent.Level.FATAL, throwable));
    }

    public void addError(Object location, String message, Throwable throwable) {
        add(new StatusEvent(location, message, StatusEvent.Level.ERROR, throwable));
    }

    public void addInfo(Object location, String message) {
        add(new StatusEvent(location, message, StatusEvent.Level.INFO, null));
    }

    void add(StatusEvent statusEvent) {
        if (headMessages.size() < 200) {
            headMessages.add(statusEvent);
        } else {
            tailMessages.add(statusEvent);
        }

        if (statusEvent.getLevel().toInt() <= this.threshold.toInt()) {
            System.err.println(statusEvent.formatMessage());
        }

    }

}
