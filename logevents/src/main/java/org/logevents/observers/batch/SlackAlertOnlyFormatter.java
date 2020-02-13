package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Use with SlackLogEventObserver. Only send alerting information to Slack,
 * but include a link to LogEventsServlet for further exploration
 */
public class SlackAlertOnlyFormatter extends SlackLogEventsFormatter {

    public SlackAlertOnlyFormatter() {
    }

    public SlackAlertOnlyFormatter(Optional<String> username, Optional<String> channel) {
        super(username, channel);
    }

    @Override
    protected String formatMessage(LogEvent logEvent) {
        return logEvent.getMessage();
    }

    @Override
    protected List<Map<String, Object>> createAttachments(LogEventBatch batch) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        result.add(createBatchAttachment(batch));
        return result;
    }

    private Map<String, Object> createBatchAttachment(LogEventBatch batch) {
        LogEvent event = batch.firstHighestLevelLogEventGroup().headMessage();
        Map<String, Object> attachment = new HashMap<>();
        if (event.getMarker() != null) {
            attachment.put("title", event.getMarker().getName());
        }
        attachment.put("text", createText(batch.firstHighestLevelLogEventGroup()));
        attachment.put("color", getColor(event.getLevel()));
        attachment.put("fields", createDetailsField(event));
        return attachment;
    }

    private String createText(LogEventGroup mainGroup) {
        LogEvent event = mainGroup.headMessage();
        Throwable throwable = event.getRootThrowable();
        String exceptionInfo = "";
        if (throwable != null) {
            exceptionInfo = throwable.getMessage() + " <" + throwable.getClass().getName() + "> ";
        }
        String formattedMessage = detailUrl
                .map(url -> "<" + detailLink(event, url) + "|" + formatMessage(event) + ">")
                .orElseGet(() -> formatMessage(event));
        return JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()) + " "
                + exceptionInfo
                + formattedMessage
                + " [" + event.getAbbreviatedLoggerName(10) + "]"
                + (mainGroup.size() > 1 ? " (" + mainGroup.size() + " repetitions)" : "")
                + (event.getLevel() == Level.ERROR ? " <!channel>" : "");
    }
}
