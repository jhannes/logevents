package org.logevents.observers.batch;

import org.junit.Ignore;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        LogEvent event = new LogEventSampler().build();

        Map<String, Object> slackMessage = new SlackLogEventsFormatter(Optional.of(userName), Optional.of(channelName)).createMessage(new LogEventBatch().add(event));
        assertEquals(channelName, JsonUtil.getField(slackMessage, "channel"));
        assertContains(event.getMessage(), JsonUtil.getField(slackMessage, "text").toString());

        List<Object> fields = JsonUtil.getList(JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 0), "fields");
        assertEquals(event.getLevel().toString(), JsonUtil.getField(JsonUtil.getObject(fields, 0), "value"));
    }

    private String randomString() {
        return Long.toString(random.nextLong () & Long.MAX_VALUE, 36);
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

        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 1);
        assertEquals("Throttled log events", JsonUtil.getField(suppressedEventsAttachment, "title"));
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
        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(message, "attachments"), 1);
        assertEquals("Throttled log events", JsonUtil.getField(suppressedEventsAttachment, "title"));
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
        SlackExceptionFormatter formatter = new SlackExceptionFormatter();
        formatter.addPackageMavenLocation("org.logevents", "org.logevents/logevents");

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
        SlackExceptionFormatter formatter = new SlackExceptionFormatter();
        formatter.addPackageBitbucket5Location("com.atlassian.labs.hipchat",
                "https://bitbucket.org/atlassian/hipchat-for-jira/src/51fead28c419edae0aa120e0f03c78d043cc81a5/",
                Optional.of("hipchat-for-jira-plugin-1.3.7"));

        StringBuilder builder = new StringBuilder();
        StackTraceElement frame = new StackTraceElement("com.atlassian.labs.hipchat.action.Configuration",
                "setHipChatAuthToken", "Configuration.java", 35);
        formatter.outputStackFrame(frame , 0, "", builder);

        assertContains("https://bitbucket.org/atlassian/hipchat-for-jira/src",
                builder.toString());
        assertContains("src/main/java/com/atlassian/labs/hipchat/action/Configuration.java?at=hipchat-for-jira-plugin-1.3.7#35",
                builder.toString());
    }

    @Test
    public void shouldReadSourceLinkFromPomFile() throws IOException, ParserConfigurationException, SAXException {
        SlackExceptionFormatter formatter = new SlackExceptionFormatter();
        try (FileInputStream pomResource = new FileInputStream("../pom.xml")) {
            formatter.addPackageMavenLocation("org.logevents", pomResource);
        }
        StringBuilder builder = new StringBuilder();
        StackTraceElement frame = new StackTraceElement(SlackExceptionFormatter.class.getName(),
                "addPackageMavenLocation", "SlackExceptionFormatter.java", 35);
        formatter.outputStackFrame(frame , 0, "", builder);

        assertContains("https://github.com/jhannes/logevents/",
                builder.toString());
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

    private <T> T pickOne(T[] options) {
        return options[new Random().nextInt(options.length)];
    }

}
