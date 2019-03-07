package org.logevents.observers;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.smtp.SmtpLogEventBatchProcessor;
import org.logevents.util.Configuration;
import org.slf4j.event.Level;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmtpLogEventObserverTest {

    @Test
    public void shouldConfigureSmtpLogEventObserver() {
        Properties properties = createConfig();
        SmtpLogEventObserver observer = new SmtpLogEventObserver(createConfiguration());
        assertEquals("SmtpLogEventObserver{batchProcessor=SmtpLogEventBatchProcessor{smtpHost=smtp.example.com}}", observer.toString());
    }


    @Test
    public void shouldFormatBatch() throws MessagingException, IOException {
        SmtpLogEventBatchProcessor batchProcessor = new SmtpLogEventBatchProcessor(createConfiguration());

        LogEventBatch batch = new LogEventBatch();
        Instant eventTime = Instant.ofEpochMilli(1529655082000L);
        batch.add(new LogEvent("org.example", Level.INFO, eventTime, null, "Some less important info", new Object[0]));
        batch.add(new LogEvent("org.example", Level.WARN, eventTime, null, "Something went wrong", new Object[0]));

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Colombo"));
        MimeMessage message = batchProcessor.formatMessage(batch, null);
        assertEquals("jhannes@example.com", message.getFrom()[0].toString());
        assertTrue(message.getSubject() + " should end with <" + "Something went wrong" + ">",
                message.getSubject().endsWith("Something went wrong"));
        assertEquals("* 13:41:22 INFO: Some less important info\n" +
                        "* 13:41:22 WARN: Something went wrong\n",
                    message.getContent().toString());
    }

    private Configuration createConfiguration() {
        return new Configuration(createConfig(), "observer.smtp");
    }

    private Properties createConfig() {
        Properties properties = new Properties();
        properties.setProperty("observer.smtp.fromAddress", "jhannes@example.com");
        properties.setProperty("observer.smtp.recipients", "operations@example.com");
        properties.setProperty("observer.smtp.host", "smtp.example.com");
        properties.setProperty("observer.smtp.password", "xxxxx");
        return properties;
    }
}
