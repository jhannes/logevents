package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.MessageFormatter;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Format log events to Microsoft Teams. See <a href="https://messagecardplayground.azurewebsites.net/">to
 * experiment with content</a>.
*/
public class MicrosoftTeamsMessageFormatter implements JsonLogEventsBatchFormatter {

    private MessageFormatter messageFormatter;
    private Optional<String> detailUrl;
    private final String applicationNode;
    private List<String> includedMdcKeys = null;
    private MicrosoftTeamsExceptionFormatter exceptionFormatter;

    static String getLevelColor(Level level) {
        return colors.get(level);
    }

    public MicrosoftTeamsMessageFormatter(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public MicrosoftTeamsMessageFormatter(Configuration configuration) {
        detailUrl = configuration.optionalString("detailUrl");
        messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class, TeamsMessageFormatter.class);
        exceptionFormatter = configuration.createInstanceWithDefault("exceptionFormatter", MicrosoftTeamsExceptionFormatter.class);
        applicationNode = configuration.getApplicationNode();
        configuration.checkForUnknownFields();
    }

    @Override
    public Map<String, Object> createMessage(LogEventBatch batch) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("@type", "MessageCard");
        message.put("text", createText(batch));
        message.put("themeColor", getLevelColor(batch.firstHighestLevelLogEventGroup().getLevel()));
        List<Map<String, Object>> sections = new ArrayList<>();
        Map<String, Object> overviewSection = new HashMap<>();
        overviewSection.put("activitySubtitle", applicationNode);
        detailUrl.ifPresent(uri ->
                overviewSection.put("potentialAction",
                        Arrays.asList(createUriAction(messageLink(uri, batch.firstHighestLevelLogEventGroup().headMessage()))))
        );
        sections.add(overviewSection);

        Map<String, String> mdcProperties = batch.firstHighestLevelLogEventGroup().headMessage().getMdcProperties();
        List<Map<String, Object>> facts = new ArrayList<>();
        for (Map.Entry<String, String> entry : mdcProperties.entrySet()) {
            if (includedMdcKeys == null || includedMdcKeys.contains(entry.getKey())) {
                facts.add(createSingleFact(entry.getKey(), entry.getValue()));
            }
        }
        if (!facts.isEmpty()) {
            HashMap<String, Object> mdcSection = new HashMap<>();
            mdcSection.put("facts", facts);
            sections.add(mdcSection);
        }
        if (batch.firstHighestLevelLogEventGroup().headMessage().getThrowable() != null) {
            sections.add(createExceptionSection(batch.firstHighestLevelLogEventGroup().headMessage().getThrowable()));
        }

        message.put("sections", sections);
        return message;
    }

    private Map<String, Object> createExceptionSection(Throwable throwable) {
        Map<String, Object> section = new HashMap<>();
        section.put("title", "**" + throwable + "**");
        section.put("text", exceptionFormatter.format(throwable));
        return section;
    }

    private String messageLink(String url, LogEvent event) {
        return url + "#instant=" + event.getInstant() + "&thread=" + event.getThreadName() + "&interval=PT10S";
    }

    private Map<String, Object> createUriAction(String uri) {
        Map<String, Object> action = new HashMap<>();
        action.put("@type", "OpenUri");
        action.put("name", "See details");
        Map<String, Object> target = new HashMap<>();
        target.put("uri", uri);
        target.put("os", "default");
        action.put("targets", Arrays.asList(target));
        return action;
    }

    private Map<String, Object> createSingleFact(String name, String value) {
        HashMap<String, Object> fact = new HashMap<>();
        fact.put("name", name);
        fact.put("value", value);
        return fact;
    }

    protected String createText(LogEventBatch batch) {
        List<String> lines = new ArrayList<>();
        for (LogEventGroup group : batch.groups()) {
            LogEvent event = group.headMessage();
            Throwable throwable = event.getRootThrowable();
            String exceptionInfo = "";
            if (throwable != null) {
                exceptionInfo = throwable.getMessage() + " (**" + throwable.getClass().getName() + "**). ";
            }
            lines.add((batch.groups().size() > 1 ? "* " : "")
                    + JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()) + " "
                    + exceptionInfo
                    + formatMessage(event)
                    + (group.size() > 1 ? " (" + group.size() + " repetitions)" : "")
                    + (batch.size() > 1 ? " (more)" : ""));
        }
        return String.join("\n", lines);
    }

    protected static Map<Level, String> colors = new HashMap<>();
    static {
        colors.put(Level.ERROR, "fd6a02");
        colors.put(Level.WARN, "f8a502");
        colors.put(Level.INFO, "1034a6");
    }

    protected String formatMessage(LogEvent event) {
        return messageFormatter.format(event.getMessage(), event.getArgumentArray());
    }

    public void setIncludedMdcKeys(List<String> includedMdcKeys) {
        this.includedMdcKeys = includedMdcKeys;
    }

    public void setPackageFilter(List<String> packageFilter) {
        this.exceptionFormatter.setPackageFilter(packageFilter);
    }

    public static class TeamsMessageFormatter extends MessageFormatter {
        @Override
        protected void outputArgument(StringBuilder result, Object arg) {
            result.append("_").append(toString(arg)).append("_");
        }
    }
}
