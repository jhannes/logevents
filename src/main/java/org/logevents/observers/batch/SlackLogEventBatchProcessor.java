package org.logevents.observers.batch;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.logevents.LogEvent;
import org.logevents.util.NetUtils;
import org.logevents.util.JsonUtil;

public class SlackLogEventBatchProcessor implements LogEventBatchProcessor {

    private String username;
    private String channel;
    private URL slackUrl;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setSlackUrl(URL slackUrl) {
        this.slackUrl = slackUrl;
    }

    @Override
    public void processBatch(List<LogEventGroup> batch) {
        try {
            sendSlackMessage(batch);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    protected void sendSlackMessage(List<LogEventGroup> batch) throws IOException {
        Map<String, Object> slackMessage = createSlackMessage(batch);
        NetUtils.postJson(slackUrl, JsonUtil.toJson(slackMessage));
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

    protected Map<String, Object> createSlackMessage(List<LogEventGroup> batch) {
        LogEventGroup mainGroup = firstHighestLevelLogEventGroup(batch);

        Map<String, Object> message = new HashMap<>();
        message.put("username", this.username);
        message.put("channel", this.channel);
        message.put("attachments", createAttachments(mainGroup, batch));
        message.put("text", createText(mainGroup));
        return message;
    }

    protected String createText(LogEventGroup mainGroup) {
        LogEvent event = mainGroup.headMessage();
        String loggerName = event.getLoggerName();
        Throwable throwable = event.getRootThrowable();
        String exceptionInfo = "";
        if (throwable != null) {
            exceptionInfo = throwable.getMessage() + " <" + throwable.getClass().getName() + "> ";
        }
        return event.getLevel().toString().substring(0, 1) + " "
            + exceptionInfo
            + event.formatMessage()
            + " [" + loggerName.substring(loggerName.lastIndexOf(".")+1, loggerName.length()) + "]"
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
        HashMap<String, Object> attachment = new HashMap<>();
        attachment.put("title", "Stack Trace");
        attachment.put("color", "danger");
        attachment.put("fields", Arrays.asList(
                slackMessageField("Exception", event.getThrowable().getClass().getSimpleName(), true),
                slackMessageField("Message", event.getThrowable().getMessage(), false)
                ));
        attachment.put("mrkdwn_in", Arrays.asList("text"));
        attachment.put("text", createStackTraceText(event));
        return attachment;
    }

    protected Map<String, Object> slackMessageField(String title, String value, boolean isShort) {
        Map<String, Object> field = new HashMap<>();
        field.put("title", title);
        field.put("value", value);
        field.put("short", isShort);
        return field;
    }

    protected String createStackTraceText(LogEvent event) {
        Throwable throwable = event.getRootThrowable();
        StringBuilder result = new StringBuilder();
        result.append(bold(throwable.getClass().getName()));
        result.append(": ").append(throwable.getMessage()).append("\n");
        for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
            result.append(asString(stackTraceElement)).append("\n");
        }
        return result.toString();
    }

    protected String asString(StackTraceElement stackTraceElement) {
        String sourceLink = getSourceLink(stackTraceElement);
        if (sourceLink == null) {
            return ">" + stackTraceElement;
        } else {
            return "><" + sourceLink + "|" + stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName() + ">";
        }
    }

    protected String getSourceLink(StackTraceElement stackTraceElement) {
        if (stackTraceElement.getClassName().startsWith("org.logevents")) {
            return "https://github.com/jhannes/logevents/tree/master/src/main/java/"
                    + stackTraceElement.getClassName().replaceAll("\\.", "/") + ".java#L" + stackTraceElement.getLineNumber();
        }
        return null;
    }

    protected String bold(String s) {
        return "*" + s + "*";
    }

}
