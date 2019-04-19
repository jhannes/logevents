package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.formatting.MessageFormatter;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Formats log event batches for suitable display on Slack. Writes the most important
 * log event as messages text and creates attachments for MDCs, Markers, Level etc.
 * Inherit to create your own slack experience.
 */
public class SlackLogEventsFormatter implements JsonLogEventsBatchFormatter {

    private MessageFormatter messageFormatter = new MessageFormatter();
    private SlackExceptionFormatter exceptionFormatter = new SlackExceptionFormatter();
    private Optional<String> username = Optional.empty();
    private Optional<String> channel = Optional.empty();
    private boolean showRepeatsIndividually;
    private Optional<String> detailUrl = Optional.empty();

    public SlackLogEventsFormatter() {
    }

    public SlackLogEventsFormatter(Optional<String> username, Optional<String> channel) {
        this.username = username;
        this.channel = channel;
    }

    @Override
    public Map<String, Object> createMessage(LogEventBatch batch) {
        Map<String, Object> message = new LinkedHashMap<>();
        username.ifPresent(u -> message.put("username", u));
        if (!username.isPresent()) {
            getHostname().ifPresent(h -> message.put("username", h));
        }
        channel.ifPresent(c -> message.put("channel", c));

        message.put("attachments", createAttachments(batch));
        message.put("text", createText(batch.firstHighestLevelLogEventGroup()));
        return message;
    }

    protected String createText(LogEventGroup mainGroup) {
        LogEvent event = mainGroup.headMessage();
        Throwable throwable = event.getRootThrowable();
        String exceptionInfo = "";
        if (throwable != null) {
            exceptionInfo = throwable.getMessage() + " <" + throwable.getClass().getName() + "> ";
        }
        return JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()) + " "
            + exceptionInfo
            + formatMessage(event)
            + " [" + event.getAbbreviatedLoggerName(10) + "]"
            + (mainGroup.size() > 1 ? " (" + mainGroup.size() + " repetitions)" : "")
            + (event.getLevel() == Level.ERROR ? " <!channel>" : "");
    }

    protected List<Map<String, Object>> createAttachments(LogEventBatch batch) {
        LogEvent event = batch.firstHighestLevelLogEventGroup().headMessage();
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        result.add(createDetailsAttachment(event));
        if (event.getThrowable() != null) {
            result.add(createStackTraceAttachment(event));
        }
        if (batch.size() > 1) {
            result.add(createThrottledEventsAttachment(batch));
        }
        return result;
    }

    protected Map<String, Object> createThrottledEventsAttachment(LogEventBatch batch) {
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("title", "Throttled log events");
        Level level = batch.firstHighestLevelLogEventGroup().headMessage().getLevel();
        attachment.put("color", getColor(level));
        attachment.put("mrkdwn_in", Arrays.asList("text"));

        StringBuilder text = new StringBuilder();
        if (showRepeatsIndividually) {
            for (LogEvent logEvent : batch) {
                String message = formatMessage(logEvent);
                text.append(JsonLogEventsBatchFormatter.emojiiForLevel(logEvent.getLevel()))
                        .append(" _").append(logEvent.getLocalTime()).append("_: ");
                text.append(message);
                text.append("\n");
            }
        } else {
            for (LogEventGroup group : batch.groups()) {
                LogEvent logEvent = group.headMessage();
                if (group.size() > 1) {
                    String message = group.headMessage().getMessage();
                    text.append("*").append(" _").append(group.headMessage().getZonedDateTime().toLocalTime()).append("_: ");
                    text.append(batch.isMainGroup(group) ? bold(message) : message);
                    text.append(" (").append(group.size()).append(" repetitions)\n");
                } else {
                    String message = formatMessage(group.headMessage());
                    text.append("*").append(" _").append(group.headMessage().getZonedDateTime().toLocalTime()).append("_: ");
                    text.append(batch.isMainGroup(group) ? bold(message) : message);
                    text.append("\n");
                }
            }
        }
        attachment.put("text", text);
        return attachment;
    }

    private String formatMessage(LogEvent logEvent) {
        return messageFormatter.format(logEvent.getMessage(), logEvent.getArgumentArray());
    }

    private String getColor(Level level) {
        return level == Level.ERROR ? "danger" : (level == Level.WARN ? "warning" : "good");
    }

    protected Map<String, Object> createDetailsAttachment(LogEvent event) {
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("title", "Details");
        detailUrl.ifPresent(url -> attachment.put("title_link",
                url + "?instant=" + event.getInstant() + "&thread=" + event.getThreadName() + "&interval=PT10S"));
        attachment.put("color", getColor(event.getLevel()));
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(slackMessageField("Level", event.getLevel().toString(), true));
        fields.add(slackMessageField("Source", getMessageSource(), true));
        fields.add(slackMessageField("Main", System.getProperty("sun.java.command"), true));
        if (event.getMarker() != null) {
            fields.add(slackMessageField("Marker", event.getMarker().getName(), true));
        }
        for (Map.Entry<String, String> entry : event.getMdcProperties().entrySet()){
            fields.add(slackMessageField(entry.getKey(), entry.getValue(), false));
        }
        attachment.put("fields", fields);
        return attachment;
    }

    private String getMessageSource() {
        String username = System.getProperty("user.name");
        return username + "@" + getHostname().orElse("unknown host");
    }

    private Optional<String> getHostname() {
        try {
            String hostname = Optional.ofNullable(System.getenv("HOSTNAME"))
                        .orElse(Optional.ofNullable(System.getenv("HTTP_HOST"))
                        .orElse(Optional.ofNullable(System.getenv("COMPUTERNAME"))
                        .orElse(InetAddress.getLocalHost().getHostName())));
            return Optional.of(hostname);
        } catch (UnknownHostException ignored) {
            return Optional.empty();
        }
    }

    protected Map<String, Object> createStackTraceAttachment(LogEvent event) {
        HashMap<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("title", "Stack Trace");
        attachment.put("color", getColor(event.getLevel()));
        attachment.put("fields", Arrays.asList(
                slackMessageField("Exception", event.getThrowable().getClass().getSimpleName(), true),
                slackMessageField("Message", event.getThrowable().getMessage(), false)
                ));
        attachment.put("mrkdwn_in", Arrays.asList("text"));
        attachment.put("text",
                "```\n" + exceptionFormatter.format(event.getThrowable()) + "```");
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

    public void addPackageGithubLocation(String sourcePackages, String project, Optional<String> tag) {
        exceptionFormatter.addPackageGithubLocation(sourcePackages, project, tag);
    }

    public void addPackageBitbucket5Location(String sourcePackages, String url, Optional<String> tag) {
        exceptionFormatter.addPackageBitbucket5Location(sourcePackages, url, tag);
    }

    public void addPackageMavenLocation(String sourcePackages, String mavenLocation) {
        exceptionFormatter.addPackageMavenLocation(sourcePackages, mavenLocation);
    }

    public void setPackageFilter(String[] packageFilter) {
        exceptionFormatter.setPackageFilter(packageFilter);
    }

    public void setUsername(Optional<String> username) {
        this.username = username;
    }

    public void setChannel(Optional<String> channel) {
        this.channel = channel;
    }

    public void setShowRepeatsIndividually(boolean showRepeatsIndividually) {
        this.showRepeatsIndividually = showRepeatsIndividually;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{username=" + username.orElse("") + ",channel=" + channel.orElse("") + "}";
    }

    public void configureSourceCode(Configuration configuration) {
        exceptionFormatter.configureSourceCode(configuration);
    }

    public void setDetailUrl(Optional<String> detailUrl) {
        this.detailUrl = detailUrl;
    }
}
