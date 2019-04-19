package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.formatting.MessageFormatter;
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

public class MicrosoftTeamsMessageFormatter implements JsonLogEventsBatchFormatter {

    private MessageFormatter messageFormatter = new MessageFormatter();

    @Override
    public Map<String, Object> createMessage(LogEventBatch batch) {
        Map<String, Object> message = new LinkedHashMap<>();

        message.put("themeColor", colors.get(batch.firstHighestLevelLogEventGroup().getLevel()));
        message.put("title", createTitle(batch));
        message.put("text", createText(batch));
        message.put("summary", batch.firstHighestLevelLogEventGroup().getLevel() + " message from " + getHostname());
        message.put("sections", createSections(batch));

        return message;
    }

    private List<Map<String, Object>> createSections(LogEventBatch batch) {
        HashMap<String, Object> overviewSection = new HashMap<>();
        overviewSection.put("activityTitle", "Message details");
        overviewSection.put("facts", new ArrayList<>(Arrays.asList(
                createSingleFact("Level", batch.firstHighestLevelLogEventGroup().getLevel().toString()),
                createSingleFact("Server", getMessageSource()),
                createSingleFact("Main", System.getProperty("sun.java.command")),
                createSingleFact("Repetitions", String.valueOf(batch.firstHighestLevelLogEventGroup().size())),
                createSingleFact("Batched message", String.valueOf(batch.size()))
        )));

        HashMap<String, Object> mdcSection = new HashMap<>();
        mdcSection.put("activityTitle", "Message diagnostics");
        List<Map<String, Object>> facts = new ArrayList<>();
        for (Map.Entry<String, String> entry : batch.firstHighestLevelLogEventGroup().headMessage().getMdcProperties().entrySet()){
            facts.add(createSingleFact(entry.getKey(), entry.getValue()));
        }
        mdcSection.put("facts", facts);

        return Arrays.asList(overviewSection, mdcSection);
    }


    private Map<String, Object> createSingleFact(String name, String value) {
        HashMap<String, Object> fact = new HashMap<>();
        fact.put("name", name);
        fact.put("value", value);
        return fact;
    }

    private String createTitle(LogEventBatch batch) {
        LogEvent event = batch.firstHighestLevelLogEventGroup().headMessage();
        Throwable throwable = event.getRootThrowable();
        String exceptionInfo = "";
        if (throwable != null) {
            exceptionInfo = throwable.getMessage() + " (" + throwable.getClass().getName() + "). ";
        }
        return event.getLevel().toString().substring(0, 1) + " "
                + exceptionInfo
                + formatMessage(event)
                + (batch.size() > 1 ? " (more)" : "");
    }

    private static Map<Level, String> colors = new HashMap<>();
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
        return event.getLevel().toString().substring(0, 1) + " "
                + exceptionInfo
                + formatMessage(event)
                + " [" + event.getAbbreviatedLoggerName(10) + "]"
                + (mainGroup.size() > 1 ? " (" + mainGroup.size() + " repetitions)" : "")
                + (batch.groups().size() > 1 ? " (" + batch.groups().size() + " unique messages)" : "")
                + (event.getLevel() == Level.ERROR ? " <!channel>" : "");
    }

    private String getMessageSource() {
        String username = System.getProperty("user.name");
        return username + "@" + getHostname();
    }

    private String getHostname() {
        String hostname = "unknown host";
        try {
            hostname = Optional.ofNullable(System.getenv("HOSTNAME"))
                    .orElse(Optional.ofNullable(System.getenv("HTTP_HOST"))
                            .orElse(Optional.ofNullable(System.getenv("COMPUTERNAME"))
                                    .orElse(InetAddress.getLocalHost().getHostName())));
        } catch (UnknownHostException ignored) {
        }
        return hostname;
    }

    private String formatMessage(LogEvent event) {
        return messageFormatter.format(event.getMessage(), event.getArgumentArray());
    }

}
