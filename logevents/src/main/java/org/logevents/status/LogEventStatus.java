package org.logevents.status;

import org.logevents.status.StatusEvent.StatusLevel;
import org.logevents.util.CircularBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LogEventStatus {

    private static LogEventStatus instance = new LogEventStatus();

    public static LogEventStatus getInstance() {
        return instance;
    }

    private List<StatusEvent> headMessages = new ArrayList<>();
    private CircularBuffer<StatusEvent> tailMessages = new CircularBuffer<>();

    public StatusEvent.StatusLevel getThreshold() {
        return StatusLevel.valueOf(System.getProperty("logevents.status", StatusLevel.ERROR.toString()));
    }

    public StatusLevel setThreshold(StatusLevel threshold) {
        StatusLevel oldThreshold = getThreshold();
        System.setProperty("logevents.status", threshold.toString());
        return oldThreshold;
    }

    public void addFatal(Object location, String message, Throwable throwable) {
        add(new StatusEvent(location, message, StatusEvent.StatusLevel.FATAL, throwable));
    }

    public void addError(Object location, String message, Throwable throwable) {
        add(new StatusEvent(location, message, StatusEvent.StatusLevel.ERROR, throwable));
    }

    public void addInfo(Object location, String message) {
        add(new StatusEvent(location, message, StatusEvent.StatusLevel.INFO, null));
    }

    void add(StatusEvent statusEvent) {
        if (headMessages.size() < 200) {
            headMessages.add(statusEvent);
        } else {
            tailMessages.add(statusEvent);
        }

        if (this.getThreshold().toInt() <= statusEvent.getLevel().toInt()) {
            System.err.println("LogEvent configuration " + statusEvent.getLevel() + ": " + statusEvent.formatMessage());
            if (statusEvent.getThrowable() != null) {
                statusEvent.getThrowable().printStackTrace();
            }
        }
    }

    public List<StatusEvent> getHeadMessages(Object target, StatusLevel threshold) {
        return headMessages.stream()
                .filter(event -> event.getLocation() == target && threshold.toInt() <= event.getLevel().toInt())
                .collect(Collectors.toList());
    }

    public StatusEvent lastMessage() {
        if (!tailMessages.isEmpty()) {
            return tailMessages.get(tailMessages.size()-1);
        } else if (!headMessages.isEmpty()) {
            return headMessages.get(headMessages.size()-1);
        } else {
            return null;
        }
    }


}
