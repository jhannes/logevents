package org.logevents.observers.smtp;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.config.LogEventConfigurationException;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.LogEventGroup;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class SmtpLogEventBatchProcessor implements LogEventBatchProcessor {
    private final MessageFormatter messageFormatter;
    private final String nodeName;
    private String fromAddress;
    private String recipients;
    private String smtpUsername;
    private String smtpPassword;
    private Properties props;

    public SmtpLogEventBatchProcessor(Configuration configuration) {
        this.fromAddress = configuration.getString("fromAddress");
        this.recipients = configuration.getString("recipients");
        this.smtpUsername = configuration.optionalString("username").orElse(fromAddress);
        this.smtpPassword = configuration.getString("password");
        this.messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class);
        this.nodeName = configuration.getNodeName();

        props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", configuration.getString("host"));
        props.put("mail.smtp.port", configuration.optionalString("port").orElse("587"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.user", configuration.getString("host"));
        props.put("mail.smtp.password", smtpPassword);

        try {
            Class.forName("com.sun.mail.util.MailLogger");
        } catch (ClassNotFoundException ignored) {
            throw new LogEventConfigurationException("You have to include com.sun.mail:javax.mail in your classpath to use " + getClass().getName());
        }


    }

    @Override
    public void processBatch(LogEventBatch batch) {
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            Transport.send(formatMessage(batch, session));
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public MimeMessage formatMessage(LogEventBatch batch, Session session) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(fromAddress);
        message.setRecipients(Message.RecipientType.TO, recipients);
        LogEvent logEvent = batch.firstHighestLevelLogEventGroup().headMessage();
        message.setSubject("[" + nodeName + "] " + messageFormatter.format(logEvent.getMessage(), logEvent.getArgumentArray()));
        message.setText(formatMessageBatch(batch));
        return message;
    }

    private String formatMessageBatch(LogEventBatch batch) {
        StringBuilder text = new StringBuilder();
        for (LogEventGroup group : batch.groups()) {
            LogEvent logEvent = group.headMessage();
            String message = messageFormatter.format(logEvent.getMessage(), logEvent.getArgumentArray());
            if (group.size() > 1) {
                message += " (" + group.size() + " repetitions)";
            }
            text.append("* ")
                    .append(logEvent.getLocalTime())
                    .append(" ").append(logEvent.getLevel()).append(": ")
                    .append(message)
                    .append("\n");
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{smtpHost=" + props.getProperty("mail.smtp.host") + "}";
    }
}
