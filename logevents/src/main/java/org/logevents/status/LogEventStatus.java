package org.logevents.status;

import org.logevents.config.Configuration;
import org.logevents.status.StatusEvent.StatusLevel;
import org.logevents.util.CircularBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Keeps tracks and optionally outputs internal messages from Log Events during configuration and usage.
 *
 * Sample configuration (in logevents.properties or as System properties):
 * <pre>
 * logevents.status=CONFIG
 * logevents.status.SlackLogEventObserver=TRACE
 * </pre>
 */
public class LogEventStatus {

    private static LogEventStatus instance = new LogEventStatus();

    public static LogEventStatus getInstance() {
        return instance;
    }

    private List<StatusEvent> headMessages = new ArrayList<>();
    private CircularBuffer<StatusEvent> tailMessages = new CircularBuffer<>();

    public void configure(Configuration configuration) {
        addDebug(this, "Configuring");

        configuration.optionalString("status").ifPresent(s -> {
            System.setProperty("logevents.status", s);
            addDebug(this, "Set threshold " + StatusLevel.valueOf(s));
        });
        for (String status : configuration.listProperties("status")) {
            String level = configuration.getString("status." + status);
            System.setProperty("logevents.status." + status, level);
            addDebug(this, "Set threshold " + StatusLevel.valueOf(level) + " for " + status);
        }
    }

    public StatusEvent.StatusLevel getThreshold(Object location) {
        if (location != null) {
            String locationStatus = System.getProperty("logevents.status." + location.getClass().getSimpleName());
            if (locationStatus != null) {
                return StatusLevel.valueOf(locationStatus);
            }
        }
        return getThreshold();
    }

    private StatusLevel getThreshold() {
        return StatusLevel.valueOf(System.getProperty("logevents.status", StatusLevel.INFO.toString()));
    }

    public StatusLevel setThreshold(StatusLevel threshold) {
        StatusLevel oldThreshold = getThreshold(null);
        System.setProperty("logevents.status", threshold.toString());
        return oldThreshold;
    }

    public void addFatal(Object location, String message, Throwable throwable) {
        add(new StatusEvent(location, message, StatusEvent.StatusLevel.FATAL, throwable));
    }

    public void addError(Object location, String message, Throwable throwable) {
        add(new StatusEvent(location, message, StatusEvent.StatusLevel.ERROR, throwable));
    }

    /**
     * Used to notify that significant events have occurred, such as a configuration reload. Enabled by default.
     */
    public void addInfo(Object location, String message) {
        add(new StatusEvent(location, message, StatusLevel.INFO, null));
    }

    /**
     * Used to provide details about configuration such as files that have been loaded
     */
    public void addConfig(Object location, String message) {
        add(new StatusEvent(location, message, StatusEvent.StatusLevel.CONFIG, null));
    }

    /**
     * Used to notify of significant details during configuration operation,
     * such as which observers are configured and how
     */
    public void addDebug(Object location, String message) {
        add(new StatusEvent(location, message, StatusLevel.DEBUG, null));
    }

    /**
     * Used to notify of significant actions during normal operation,
     * for example every time data is submitted to an external API
     */
    public void addTrace(Object location, String message) {
        add(new StatusEvent(location, message, StatusEvent.StatusLevel.TRACE, null));
    }

    // TODO: We should probably detect duplicates (same location, level and message)
    //   and just record repeat count and last timestamp. System.err should not happen on duplicates
    //   (or perhaps, not on duplicates within 10 minutes)
    void add(StatusEvent statusEvent) {
        if (headMessages.size() < 1000) {
            headMessages.add(statusEvent);
        } else {
            tailMessages.add(statusEvent);
        }

        if (this.getThreshold(statusEvent.getLocation()).toInt() <= statusEvent.getLevel().toInt()) {
            System.err.println(statusEvent.getLevel() + ": " + statusEvent.formatMessage());
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

    public List<StatusEvent> getHeadMessages() {
        return headMessages;
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

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "headMessages=" + headMessages.size() + "," +
                "threshold=" + getThreshold() +
                '}';
    }

    public void clear() {
        headMessages.clear();
        tailMessages.clear();
    }

    public List<String> getHeadMessageTexts(Object target, StatusLevel level) {
        return getHeadMessages(target, level).stream().map(StatusEvent::getMessage).collect(Collectors.toList());
    }

}
