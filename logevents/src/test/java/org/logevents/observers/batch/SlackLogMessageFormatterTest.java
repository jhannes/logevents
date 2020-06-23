package org.logevents.observers.batch;

import org.junit.Ignore;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlackLogMessageFormatterTest {

    private String loggerName = getClass().getName();
    private Random random = new Random();

    @Test
    public void shouldCreateSlackMessage() {
        String userName = randomString();
        String channelName = randomString();

        LogEvent event = new LogEventSampler().withLevel(Level.INFO).build();

        Map<String, Object> slackMessage = new SlackLogEventsFormatter(Optional.of(userName), Optional.of(channelName)).createMessage(new LogEventBatch().add(event));
        assertEquals(channelName, JsonUtil.getField(slackMessage, "channel"));
        Map<String, Object> detailsAttachment = JsonUtil.getObjectList(slackMessage, "attachments").get(0);
        assertContains(event.getMessage(), JsonUtil.getField(detailsAttachment, "text").toString());

        Map<String, Object> mainAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 0);
        assertEquals("good", mainAttachment.get("color"));
    }

    @Test
    public void shouldCollectMessagesInBatch() {
        LogEventBatch batch = new LogEventBatch();
        batch.add(new LogEventSampler().withLevel(Level.WARN).withFormat("A lesser important message").build());
        LogEventSampler sampler = new LogEventSampler().withLoggerName(loggerName).withLevel(Level.ERROR).withFormat("A more important message");
        batch.add(sampler.build());
        batch.add(sampler.build());
        batch.add(new LogEventSampler().withLevel(Level.ERROR).build());

        Map<String, Object> slackMessage = new SlackLogEventsFormatter(Optional.empty(), Optional.empty())
                .createMessage(batch);

        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 0);
        assertContains(": A lesser important message",
                JsonUtil.getField(suppressedEventsAttachment, "text").toString());
        assertContains(": *A more important message* (2 repetitions)",
                JsonUtil.getField(suppressedEventsAttachment, "text").toString());
    }

    @Test
    public void canShowMessagesIndividually() {
        SlackLogEventsFormatter formatter = new SlackLogEventsFormatter(Optional.empty(), Optional.empty());
        formatter.setShowRepeatsIndividually(true);


        LogEventSampler sampler = new LogEventSampler().withFormat("Something very {} has happened {}");

        LogEventBatch batch = new LogEventBatch().add(
                sampler.withArgs("good", "yesterday").build()
        ).add(
                sampler.withArgs("bad", "today").build()
        );
        Map<String, Object> message = formatter.createMessage(batch);
        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(message, "attachments"), 0);
        String text = JsonUtil.getField(suppressedEventsAttachment, "text").toString();
        assertContains(": Something very good has happened yesterday", text);
        assertContains(": Something very bad has happened today", text);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldShowMdcInformation() {
        LogEventBatch batch = new LogEventBatch();
        batch.add(new LogEventSampler().withMdc("operation", "DELETE").withMdc("clientIp", "127.0.0.1").build());

        Map<String, Object> slackMessage = new SlackLogEventsFormatter(Optional.empty(), Optional.empty())
                .createMessage(batch);
        Map<String, Object> mdcAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 1);
        assertEquals("MDC", mdcAttachment.get("title"));
        List<Map<String, Object>> mdcFields = (List<Map<String, Object>>) mdcAttachment.get("fields");
        assertEquals("operation", mdcFields.get(0).get("title"));
        assertEquals("DELETE", mdcFields.get(0).get("value"));
        assertEquals("clientIp", mdcFields.get(1).get("title"));
        assertEquals("127.0.0.1", mdcFields.get(1).get("value"));
    }

    @Test
    public void shouldOutputStackTrace() {
        Exception exception = new IOException("Something went wrong with " + randomString());
        LogEventBatch batch = new LogEventBatch();
        batch.add(new LogEventSampler().withThrowable(exception).build());
        Map<String, Object> slackMessage = new SlackLogEventsFormatter(Optional.empty(), Optional.empty()).createMessage(batch);

        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 1);
        assertEquals("Stack Trace", JsonUtil.getField(suppressedEventsAttachment, "title"));
        assertContains("org.logevents.observers.batch.SlackLogMessageFormatterTest",
                JsonUtil.getField(suppressedEventsAttachment, "text").toString());
    }

    @Test
    @Ignore("How do we reliably find a pom.xml-file in classpath?")
    public void shouldCreateSourceLinkInStackTrace() {
        Properties properties = new Properties();
        properties.put("formatter.sourceCode.1.package", "org.logevents");
        properties.put("formatter.sourceCode.1.maven", "org.logevents/logevents");
        SlackExceptionFormatter formatter = new SlackExceptionFormatter(new Configuration(properties, "formatter"));

        StringBuilder builder = new StringBuilder();
        StackTraceElement frame = new StackTraceElement(SlackExceptionFormatter.class.getName(),
                "addPackageMavenLocation", "SlackExceptionFormatter.java", 72);
        formatter.outputStackFrame(frame , 2, "", builder);

        assertContains("https://github.com/jhannes/logevents/blob",
                builder.toString());
        assertContains("src/main/java/org/logevents/observers/batch/SlackExceptionFormatter.java#L72",
                builder.toString());
    }

    @Test
    public void shouldCreateSourceLinkInBitbucket() {
        Properties properties = new Properties();
        properties.put("formatter.sourceCode.1.package", "com.atlassian.labs.hipchat");
        properties.put("formatter.sourceCode.1.bitbucket", "https://bitbucket.org/atlassian/hipchat-for-jira/src/51fead28c419edae0aa120e0f03c78d043cc81a5/");
        properties.put("formatter.sourceCode.1.tag", "hipchat-for-jira-plugin-1.3.7");
        SlackExceptionFormatter formatter = new SlackExceptionFormatter(new Configuration(properties, "formatter"));

        StringBuilder builder = new StringBuilder();
        StackTraceElement frame = new StackTraceElement("com.atlassian.labs.hipchat.action.Configuration",
                "setHipChatAuthToken", "Configuration.java", 35);
        formatter.outputStackFrame(frame , 0, "", builder);

        assertContains("https://bitbucket.org/atlassian/hipchat-for-jira/src",
                builder.toString());
        assertContains("src/main/java/com/atlassian/labs/hipchat/action/Configuration.java?at=hipchat-for-jira-plugin-1.3.7#35",
                builder.toString());
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

    private String randomString() {
        return Long.toString(random.nextLong () & Long.MAX_VALUE, 36);
    }

    private <T> T pickOne(T[] options) {
        return options[new Random().nextInt(options.length)];
    }

}
