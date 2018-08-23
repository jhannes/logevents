package org.logevents.observers.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;

public class SlackLogMessageFactoryTest {

    private String loggerName = getClass().getName();
    private Random random = new Random();

    @Test
    public void shouldCreateSlackMessage() {
        String userName = randomString();
        String channelName = randomString();

        String loggerName = getClass().getName();
        Level level = pickOne(Level.values());
        String format = randomString();

        List<LogEventGroup> batch = new ArrayList<>();
        batch.add(new LogEventGroup(new LogEvent(loggerName, level, format)));

        Map<String, Object> slackMessage = new SlackLogEventsFormatter().createSlackMessage(batch, Optional.of(userName), Optional.of(channelName));
        assertEquals(channelName, JsonUtil.getField(slackMessage, "channel"));
        assertContains(format, JsonUtil.getField(slackMessage, "text").toString());

        List<Object> fields = JsonUtil.getList(JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 0), "fields");
        assertEquals(level.toString(), JsonUtil.getField(JsonUtil.getObject(fields, 0), "value"));
    }

    private String randomString() {
        return Long.toString(random.nextLong () & Long.MAX_VALUE, 36);
    }

    @Test
    public void shouldCollectMessagesInBatch() {
        List<LogEventGroup> batch = new ArrayList<>();
        batch.add(new LogEventGroup(new LogEvent(loggerName, Level.WARN, "A lesser important message")));
        LogEventGroup logEventGroup = new LogEventGroup(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        logEventGroup.add(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        batch.add(logEventGroup);
        batch.add(new LogEventGroup(new LogEvent(loggerName, Level.ERROR, "Yet another message")));

        Map<String, Object> slackMessage = new SlackLogEventsFormatter().createSlackMessage(batch, Optional.empty(), Optional.empty());

        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 1);
        assertEquals("Suppressed log events", JsonUtil.getField(suppressedEventsAttachment, "title"));
        assertContains(": A lesser important message",
                JsonUtil.getField(suppressedEventsAttachment, "text").toString());
        assertContains(": *A more important message* (2 repetitions)",
                JsonUtil.getField(suppressedEventsAttachment, "text").toString());
    }

    @Test
    public void shouldOutputStackTrace() {
        Exception exception = new IOException("Something went wrong with " + randomString());
        List<LogEventGroup> batch = new ArrayList<>();
        batch.add(new LogEventGroup(new LogEvent(loggerName, Level.WARN, "A lesser important message", exception)));
        Map<String, Object> slackMessage = new SlackLogEventsFormatter().createSlackMessage(batch, Optional.empty(), Optional.empty());

        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 1);
        assertEquals("Stack Trace", JsonUtil.getField(suppressedEventsAttachment, "title"));
        assertContains("org.logevents.observers.batch.SlackLogMessageFactoryTest",
                JsonUtil.getField(suppressedEventsAttachment, "text").toString());
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

    private <T> T pickOne(T[] options) {
        return options[new Random().nextInt(options.length)];
    }

}
