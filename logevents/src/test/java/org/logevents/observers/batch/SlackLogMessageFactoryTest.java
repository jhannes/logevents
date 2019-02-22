package org.logevents.observers.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.Ignore;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

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

        LogEventBatch batch = new LogEventBatch().add(new LogEvent(loggerName, level, format));

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
        LogEventBatch batch = new LogEventBatch();
        batch.add(new LogEvent(loggerName, Level.WARN, "A lesser important message"));
        batch.add(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        batch.add(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        batch.add(new LogEvent(loggerName, Level.ERROR, "Yet another message"));

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
        LogEventBatch batch = new LogEventBatch();
        batch.add(new LogEvent(loggerName, Level.WARN, "A lesser important message", exception));
        Map<String, Object> slackMessage = new SlackLogEventsFormatter().createSlackMessage(batch, Optional.empty(), Optional.empty());

        Map<String, Object> suppressedEventsAttachment = JsonUtil.getObject(JsonUtil.getList(slackMessage, "attachments"), 1);
        assertEquals("Stack Trace", JsonUtil.getField(suppressedEventsAttachment, "title"));
        assertContains("org.logevents.observers.batch.SlackLogMessageFactoryTest",
                JsonUtil.getField(suppressedEventsAttachment, "text").toString());
    }

    @Test
    @Ignore
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
