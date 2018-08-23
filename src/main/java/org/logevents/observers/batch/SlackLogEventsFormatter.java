package org.logevents.observers.batch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.logevents.LogEvent;

public class SlackLogEventsFormatter {

    private SlackExceptionFormatter exceptionFormatter = new SlackExceptionFormatter();

    public Map<String, Object> createSlackMessage(List<LogEventGroup> batch, Optional<String> username, Optional<String> channel) {
        LogEventGroup mainGroup = firstHighestLevelLogEventGroup(batch);

        Map<String, Object> message = new LinkedHashMap<>();
        username.ifPresent(u -> message.put("username", u));
        channel.ifPresent(c -> message.put("channel", c));
        message.put("attachments", createAttachments(mainGroup, batch));
        message.put("text", createText(mainGroup));
        return message;
    }

    protected LogEventGroup firstHighestLevelLogEventGroup(List<LogEventGroup> batch) {
        LogEventGroup result = batch.get(0);
        for (LogEventGroup group : batch) {
            if (group.headMessage().getLevel().toInt() > result.headMessage().getLevel().toInt()) {
                result = group;
            }
        }
        return result;
    }

    protected String createText(LogEventGroup mainGroup) {
        LogEvent event = mainGroup.headMessage();
        Throwable throwable = event.getRootThrowable();
        String exceptionInfo = "";
        if (throwable != null) {
            exceptionInfo = throwable.getMessage() + " <" + throwable.getClass().getName() + "> ";
        }
        return event.getLevel().toString().substring(0, 1) + " "
            + exceptionInfo
            + event.formatMessage()
            + " [" + event.getAbbreviatedLoggerName(10) + "]"
            + (mainGroup.size() > 1 ? " (" + mainGroup.size() + " repetitions)" : "");
    }

    protected List<Map<String, Object>> createAttachments(LogEventGroup mainGroup, List<LogEventGroup> batch) {
        LogEvent event = mainGroup.headMessage();
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        result.add(createDetailsAttachment(event));
        if (event.getThrowable() != null) {
            result.add(createStackTraceAttachment(event));
        }
        if (batch.size() > 1) {
            result.add(createSupressedEventsAttachment(mainGroup, batch));
        }
        return result;
    }

    protected Map<String, Object> createSupressedEventsAttachment(LogEventGroup mainGroup, List<LogEventGroup> batch) {
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("title", "Suppressed log events");
        attachment.put("color", "danger");
        attachment.put("mrkdwn_in", Arrays.asList("text"));

        StringBuilder text = new StringBuilder();
        for (LogEventGroup group : batch) {
            String message = group.headMessage().formatMessage();
            if (group == mainGroup) {
                message = bold(message);
            }
            if (group.size() > 1) {
                message += " (" + group.size() + " repetitions)";
            }
            text.append("*")
                .append(" _" + group.headMessage().getZonedDateTime().toLocalTime() + "_: ")
                .append(message)
                .append("\n");
        }
        attachment.put("text", text);
        return attachment;
    }

    protected Map<String, Object> createDetailsAttachment(LogEvent event) {
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("title", "Details");
        attachment.put("color", "danger");
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(slackMessageField("Level", event.getLevel().toString(), false));
        for (Map.Entry<String, String> entry : event.getMdcProperties().entrySet()) {
            fields.add(slackMessageField(entry.getKey(), entry.getValue(), false));
        }
        attachment.put("fields", fields);
        return attachment;
    }

    protected Map<String, Object> createStackTraceAttachment(LogEvent event) {
        HashMap<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("title", "Stack Trace");
        attachment.put("color", "danger");
        attachment.put("fields", Arrays.asList(
                slackMessageField("Exception", event.getThrowable().getClass().getSimpleName(), true),
                slackMessageField("Message", event.getThrowable().getMessage(), false)
                ));
        attachment.put("mrkdwn_in", Arrays.asList("text"));
        attachment.put("text",
                "```\n" + exceptionFormatter.format(event.getThrowable()) + "\n```");
        return attachment;
    }

    protected Map<String, Object> slackMessageField(String title, String value, boolean isShort) {
        Map<String, Object> field = new HashMap<>();
        field.put("title", title);
        field.put("value", value);
        field.put("short", isShort);
        return field;
    }

    protected String bold(String s) {
        return "*" + s + "*";
    }
}
