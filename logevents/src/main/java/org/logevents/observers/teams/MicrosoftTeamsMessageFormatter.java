package org.logevents.observers.teams;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.config.MdcFilter;
import org.logevents.formatters.messages.MessageFormatter;
import org.logevents.observers.batch.JsonLogEventsBatchFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventGroup;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Format log events to Microsoft Teams. See <a href="https://messagecardplayground.azurewebsites.net/">to
 * experiment with content</a>.
 */
public class MicrosoftTeamsMessageFormatter implements JsonLogEventsBatchFormatter {

    private final MessageFormatter messageFormatter;
    private final Optional<String> detailUrl;
    private final String applicationNode;
    private MdcFilter mdcFilter = MdcFilter.INCLUDE_ALL;
    private final MicrosoftTeamsExceptionFormatter exceptionFormatter;

    static String getLevelColor(Level level) {
        return colors.get(level);
    }

    public MicrosoftTeamsMessageFormatter(Map<String, String> properties, String prefix) {
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
        sections.add(overviewSection);

        Map<String, String> mdcProperties = batch.firstHighestLevelLogEventGroup().headMessage().getMdcProperties();
        List<Map<String, Object>> facts = new ArrayList<>();
        for (Map.Entry<String, String> entry : mdcProperties.entrySet()) {
            if (mdcFilter.isKeyIncluded(entry.getKey())) {
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
            StringBuilder eventLine = new StringBuilder()
                    .append(batch.groups().size() > 1 ? "* " : "")
                    .append(messageToText(event))
                    .append(group.size() > 1 ? " (" + group.size() + " repetitions)" : "");
            detailUrl.ifPresent(uri ->
                    eventLine
                            .append(" [|See details|](")
                            .append(messageLink(uri, group.headMessage()))
                            .append(")")
            );
            lines.add(eventLine.toString());
        }
        return String.join("\n", lines);
    }

    protected String messageToText(LogEvent event) {
        Throwable throwable = event.getRootThrowable();
        return String.format("%s %s%s%s **[%s]**",
                JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()),
                event.getLevel() == Level.ERROR ? "@channel " : "",
                throwable != null ? throwable.getMessage() + " (**" + throwable.getClass().getName() + "**). " : "",
                formatMessage(event),
                event.getAbbreviatedLoggerName(0)
        );
    }

    protected String formatMessage(LogEvent event) {
        return event.getMessage(messageFormatter);
    }

    protected static Map<Level, String> colors = new HashMap<>();

    static {
        colors.put(Level.ERROR, "fd6a02");
        colors.put(Level.WARN, "f8a502");
        colors.put(Level.INFO, "1034a6");
    }

    public void setMdcFilter(MdcFilter mdcFilter) {
        this.mdcFilter = mdcFilter;
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
