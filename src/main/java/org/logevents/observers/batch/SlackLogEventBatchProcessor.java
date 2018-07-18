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
            sendSingleMessage(batch.get(0).headMessage());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void sendSingleMessage(LogEvent event) throws IOException {
        Map<String, Object> slackMessage = createSlackMessage(event);
        NetUtils.postJson(slackUrl, JsonUtil.toJson(slackMessage));
    }

    protected Map<String, Object> createSlackMessage(LogEvent event) {
        Map<String, Object> message = new HashMap<>();
        message.put("username", this.username);
        message.put("channel", this.channel);
        message.put("attachments", createAttachments(event));
        message.put("text", createText(event));
        return message;
    }

    protected Object createText(LogEvent event) {
        String loggerName = event.getLoggerName();
        Throwable throwable = event.getRootThrowable();
        String exceptionInfo = "";
        if (throwable != null) {
            exceptionInfo = throwable.getMessage() + " <" + throwable.getClass().getName() + "> ";
        }
        return event.getLevel().toString().substring(0, 1) + " "
            + exceptionInfo
            + event.formatMessage()
            + " [" + loggerName.substring(loggerName.lastIndexOf(".")+1, loggerName.length()) + "]";
    }

    protected List<Map<String, Object>> createAttachments(LogEvent event) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        if (event.getThrowable() != null) {
            result.add(createStackTraceAttachment(event));
        }
        if (!event.getMdcProperties().isEmpty()) {
            result.add(createMdcAttachment(event));
        }
        return result;
    }

    private Map<String, Object> createMdcAttachment(LogEvent event) {
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("title", "Details");
        attachment.put("color", "danger");
        List<Map<String, Object>> fields = new ArrayList<>();
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
            return "https://github.com/jhannes/logevents/master/src/main/java/"
                    + stackTraceElement.getClassName().replaceAll("\\.", "/") + ".java#L" + stackTraceElement.getLineNumber();
        }
        return null;
    }

    protected String bold(String s) {
        return "*" + s + "*";
    }

}
