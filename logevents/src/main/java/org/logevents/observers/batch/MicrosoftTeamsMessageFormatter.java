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
    private final String nodeName;

    public MicrosoftTeamsMessageFormatter(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public MicrosoftTeamsMessageFormatter(Configuration configuration) {
        detailUrl = configuration.optionalString("detailUrl");
        messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class);
        nodeName = configuration.getServerUser();
        configuration.checkForUnknownFields();
    }

    @Override
    public Map<String, Object> createMessage(LogEventBatch batch) {
        Map<String, Object> message = new LinkedHashMap<>();

        message.put("@type", "MessageCard");
        message.put("themeColor", colors.get(batch.firstHighestLevelLogEventGroup().getLevel()));
        message.put("title", createTitle(batch));
        message.put("text", createText(batch));
        message.put("summary", batch.firstHighestLevelLogEventGroup().getLevel() + " message from " + nodeName);
        message.put("sections", createSections(batch));

        return message;
    }

    protected List<Map<String, Object>> createSections(LogEventBatch batch) {
        return Arrays.asList(
                createOverviewSection(batch),
                createMdcSection(batch));
    }

    protected Map<String, Object> createOverviewSection(LogEventBatch batch) {
        LogEvent headMessage = batch.firstHighestLevelLogEventGroup().headMessage();
        Map<String, Object> overviewSection = new HashMap<>();
        overviewSection.put("activityTitle", "Message details");
        overviewSection.put("facts", new ArrayList<>(Arrays.asList(
                createSingleFact("Level", batch.firstHighestLevelLogEventGroup().getLevel().toString()),
                createSingleFact("Server", nodeName),
                createSingleFact("Main", System.getProperty("sun.java.command")),
                createSingleFact("Repetitions", String.valueOf(batch.firstHighestLevelLogEventGroup().size())),
                createSingleFact("Batched message", String.valueOf(batch.size()))
        )));
        detailUrl.ifPresent(uri ->
            overviewSection.put("potentialAction",
                    Arrays.asList(createUriAction("See details", messageLink(uri, headMessage))))
        );
        return overviewSection;
    }

    protected Map<String, Object> createMdcSection(LogEventBatch batch) {
        LogEvent headMessage = batch.firstHighestLevelLogEventGroup().headMessage();
        HashMap<String, Object> mdcSection = new HashMap<>();
        mdcSection.put("activityTitle", "Message diagnostics");
        List<Map<String, Object>> facts = new ArrayList<>();
        for (Map.Entry<String, String> entry : headMessage.getMdcProperties().entrySet()){
            facts.add(createSingleFact(entry.getKey(), entry.getValue()));
        }
        mdcSection.put("facts", facts);
        return mdcSection;
    }

    private String messageLink(String url, LogEvent event) {
        return url + "?instant=" + event.getInstant() + "&thread=" + event.getThreadName() + "&interval=PT10S";
    }

    private Map<String, Object> createUriAction(String name, String uri) {
        Map<String, Object> action = new HashMap<>();
        action.put("@type", "OpenUri");
        action.put("name", name);
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

    protected String createTitle(LogEventBatch batch) {
        LogEvent event = batch.firstHighestLevelLogEventGroup().headMessage();
        Throwable throwable = event.getRootThrowable();
        String exceptionInfo = "";
        if (throwable != null) {
            exceptionInfo = throwable.getMessage() + " (" + throwable.getClass().getName() + "). ";
        }
        return JsonLogEventsBatchFormatter.emojiiForLevel(event.getLevel()) + " "
                + exceptionInfo
                + formatMessage(event)
                + (batch.size() > 1 ? " (more)" : "");
    }

    protected static Map<Level, String> colors = new HashMap<>();
    static {
        colors.put(Level.ERROR, "fd6a02");
        colors.put(Level.WARN, "f8a502");
        colors.put(Level.INFO, "1034a6");
    }

    protected String createText(LogEventBatch batch) {
        LogEventGroup mainGroup = batch.firstHighestLevelLogEventGroup();
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
                + (batch.groups().size() > 1 ? " (" + batch.groups().size() + " unique messages)" : "")
                + (event.getLevel() == Level.ERROR ? " <!channel>" : "");
    }

    protected String formatMessage(LogEvent event) {
        return messageFormatter.format(event.getMessage(), event.getArgumentArray());
    }
}
