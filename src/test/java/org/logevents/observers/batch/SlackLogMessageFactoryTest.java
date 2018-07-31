package org.logevents.observers.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.junit.Test;
import org.logevents.LogEvent;
import org.slf4j.event.Level;

public class SlackLogMessageFactoryTest {

    private String loggerName = getClass().getName();

    @Test
    public void shouldCreateSlackMessage() {
        String userName = UUID.randomUUID().toString();
        String channelName = UUID.randomUUID().toString();

        String loggerName = getClass().getName();
        Level level = pickOne(Level.values());
        String format = UUID.randomUUID().toString();

        List<LogEventGroup> batch = new ArrayList<>();
        batch.add(new LogEventGroup(new LogEvent(loggerName, level, null, format, new Object[0])));

        Map<String, Object> slackMessage = new SlackLogMessageFactory().createSlackMessage(batch, Optional.of(userName), Optional.of(channelName));
        assertEquals(channelName, getField(slackMessage, "channel"));
        assertContains(format, getField(slackMessage, "text").toString());

        List<Object> fields = getList(getObject(getList(slackMessage, "attachments"), 0), "fields");
        assertEquals(level.toString(), getField(getObject(fields, 0), "value"));
    }

    @Test
    public void shouldCollectMessagesInBatch() {
        List<LogEventGroup> batch = new ArrayList<>();
        batch.add(new LogEventGroup(new LogEvent(loggerName, Level.WARN, null, "A lesser important message", new Object[0])));
        LogEventGroup logEventGroup = new LogEventGroup(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        logEventGroup.add(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        batch.add(logEventGroup);
        batch.add(new LogEventGroup(new LogEvent(loggerName, Level.ERROR, null, "Yet another message", new Object[0])));

        Map<String, Object> slackMessage = new SlackLogMessageFactory().createSlackMessage(batch, Optional.empty(), Optional.empty());

        Map<String, Object> suppressedEventsAttachment = getObject(getList(slackMessage, "attachments"), 1);
        assertEquals("Suppressed log events", getField(suppressedEventsAttachment, "title"));
        assertContains(": A lesser important message",
                getField(suppressedEventsAttachment, "text").toString());
        assertContains(": *A more important message* (2 repetitions)",
                getField(suppressedEventsAttachment, "text").toString());
    }

    @Test
    public void shouldOutputStackTrace() {
        Exception exception = new IOException("Something went wrong with " + UUID.randomUUID());
        List<LogEventGroup> batch = new ArrayList<>();
        batch.add(new LogEventGroup(new LogEvent(loggerName, Level.WARN, null, "A lesser important message", new Object[] { exception })));
        Map<String, Object> slackMessage = new SlackLogMessageFactory().createSlackMessage(batch, Optional.empty(), Optional.empty());

        Map<String, Object> suppressedEventsAttachment = getObject(getList(slackMessage, "attachments"), 1);
        assertEquals("Stack Trace", getField(suppressedEventsAttachment, "title"));
        assertContains("org.logevents.observers.batch.SlackLogMessageFactoryTest",
                getField(suppressedEventsAttachment, "text").toString());
    }

    private Object getField(Map<String, Object> object, String fieldName) {
        return object.get(fieldName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getObject(List<?> list, int index) {
        return (Map<String, Object>) list.get(index);
    }

    @SuppressWarnings("unchecked")
    private List<Object> getList(Map<String, Object> object, String fieldName) {
        return (List<Object>) object.get(fieldName);
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

    private <T> T pickOne(T[] options) {
        return options[new Random().nextInt(options.length)];
    }

}
