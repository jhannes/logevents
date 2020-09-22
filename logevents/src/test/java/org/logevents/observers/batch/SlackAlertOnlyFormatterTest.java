package org.logevents.observers.batch;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlackAlertOnlyFormatterTest {

    private Random random = new Random();

    @Test
    public void shouldCreateSlackMessage() {
        String userName = randomString();
        String channelName = randomString();

        LogEvent event = new LogEventSampler().withFormat("Args [{}] [{}] and [{}]").withArgs("a", "b", "c").withLevel(Level.INFO).build();

        Map<String, Object> slackMessage = new SlackAlertOnlyFormatter(Optional.of(userName), Optional.of(channelName))
                .createMessage(new LogEventBatch().add(event));
        assertEquals(channelName, JsonUtil.getField(slackMessage, "channel"));
        Map<String, Object> detailsAttachment = JsonUtil.getObjectList(slackMessage, "attachments").get(0);
        assertContains(event.getMessage(), JsonUtil.getField(detailsAttachment, "text").toString());

        Map<String, Object> mainAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 0);
        assertEquals("good", mainAttachment.get("color"));
    }

    @Test
    public void shouldShowStackTrace() {
        LogEvent event = new LogEventSampler().withThrowable().build();

        Map<String, Object> slackMessage = new SlackAlertOnlyFormatter().createMessage(new LogEventBatch().add(event));
        Map<String, Object> detailsAttachment = JsonUtil.getObjectList(slackMessage, "attachments").get(0);
        assertContains(event.getThrowable().getClass().getName(), JsonUtil.getField(detailsAttachment, "text").toString());
        assertContains(event.getThrowable().getMessage(), JsonUtil.getField(detailsAttachment, "text").toString());
        assertContains(event.getMessage(), JsonUtil.getField(detailsAttachment, "text").toString());
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

    private String randomString() {
        return Long.toString(random.nextLong () & Long.MAX_VALUE, 36);
    }

}
