package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventGroup;
import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

import java.awt.*;
import java.util.Properties;

/**
 * Displays messages in the system tray notification area of the operating system. Intended
 * for use during debugging sessions to highlight high priority messages while logging
 * fine grained to console.
 *
 * <h2>Example configuration</h2>
 *
 * <pre>
 * observer.systray=SystemTrayLogEventObserver
 * observer.systray.threshold=WARN
 * </pre>
 *
 * <h2>Issues</h2>
 *
 * At least on Windows, keeping the notification icon in the system tray prevents the JVM from
 * exiting. Therefore, logevents removes the icon after each notification, leading to a briefly
 * flashing icon in the system tray. Ideally, I'd like to keep the notification icon in the
 * system tray, but not prevent shutdown
 */
public class SystemTrayLogEventObserver extends AbstractBatchingLogEventObserver {

    private Image image;
    private String tooltip;
    private MessageFormatter messageFormatter = new MessageFormatter();
    private TrayIcon trayIcon;

    public SystemTrayLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public SystemTrayLogEventObserver(Configuration configuration) {
        configureBatching(configuration);
        configureFilter(configuration);
        configureMarkers(configuration);
        image = createImageFromResource(configuration.optionalString("icon").orElse("logevents-icon.png"));
        tooltip = configuration.optionalString("tooltip").orElse("Log Events");
        configuration.checkForUnknownFields();
        trayIcon = new TrayIcon(image, tooltip);
        trayIcon.setImageAutoSize(true);
    }

    private Image createImageFromResource(String name) {
        return Toolkit.getDefaultToolkit().createImage(getClass().getClassLoader().getResource(name));
    }

    @Override
    protected void processBatch(LogEventBatch batch) {
        if (!batch.isEmpty()) {
            LogEventGroup logEventGroup = batch.firstHighestLevelLogEventGroup();
            sendNotification(getCaption(batch), logEventGroup.headMessage().getLevel(), getText(batch));
        }
    }

    private void sendNotification(String title, Level level, String text) {
        LogEventStatus.getInstance().addTrace(this, "Displaying message " + title);
        try {
            SystemTray.getSystemTray().add(trayIcon);
            trayIcon.displayMessage(
                    title,
                    text,
                    getMessageType(level)
            );
            LogEventStatus.getInstance().addTrace(this, "Displayed message");
            SystemTray.getSystemTray().remove(trayIcon);
        } catch (AWTException e) {
            LogEventStatus.getInstance().addError(this, "Failed to display system tray icon", e);
        }
    }

    protected String getText(LogEventBatch batch) {
        LogEvent event = batch.firstHighestLevelLogEventGroup().headMessage();
        Throwable throwable = event.getRootThrowable();
        return String.format("%s %s%s [%s]",
                JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()),
                throwable != null ? throwable.getMessage() + " (" + throwable.getClass().getName() + ")\n" : "",
                formatMessage(event),
                event.getAbbreviatedLoggerName(0)
        );
    }

    protected String formatMessage(LogEvent event) {
        return messageFormatter.format(event.getMessage(), event.getArgumentArray());
    }

    protected String getCaption(LogEventBatch batch) {
        LogEventGroup eventGroup = batch.firstHighestLevelLogEventGroup();
        if (batch.groups().size() > 1) {
            return batch.size() + " messages";
        } else if (eventGroup.size() > 1) {
            return abbreviate(formatMessage(eventGroup.headMessage()), 25) + " (" + eventGroup.size() + " repetitions)";
        }
        return abbreviate(formatMessage(eventGroup.headMessage()), 35);
    }

    protected String abbreviate(String message, int maxLength) {
        return message.length() > maxLength-3 ? message.substring(0, maxLength-3) + "..." : message;
    }

    protected TrayIcon.MessageType getMessageType(Level level) {
        switch (level) {
            case ERROR:
                return TrayIcon.MessageType.ERROR;
            case WARN:
                return TrayIcon.MessageType.WARNING;
            default:
                return TrayIcon.MessageType.INFO;
        }
    }
}
