package org.logevents.observers.smtp;

import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatchProcessor;
import org.logevents.observers.batch.LogEventGroup;
import org.logevents.util.Configuration;
import org.logevents.util.LogEventConfigurationException;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Properties;

public class SmtpLogEventBatchProcessor implements LogEventBatchProcessor {
    private String fromAddress;
    private String recipients;
    private Optional<String> applicationName;
    private String smtpUsername;
    private String smtpPassword;
    private Properties props;

    public SmtpLogEventBatchProcessor(Configuration configuration) {
        this.fromAddress = configuration.getString("fromAddress");
        this.recipients = configuration.getString("recipients");
        this.applicationName = configuration.optionalString("applicationName");
        this.smtpUsername = configuration.optionalString("username").orElse(fromAddress);
        this.smtpPassword = configuration.getString("password");

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
        message.setSubject("[" + getApplicationName() + "] " + batch.firstHighestLevelLogEventGroup().headMessage().formatMessage());
        message.setText(formatMessageBatch(batch));
        return message;
    }

    private String formatMessageBatch(LogEventBatch batch) {
        StringBuilder text = new StringBuilder();
        for (LogEventGroup group : batch.groups()) {
            String message = group.headMessage().formatMessage();
            if (group.size() > 1) {
                message += " (" + group.size() + " repetitions)";
            }
            text.append("* ")
                    .append(group.headMessage().getZonedDateTime().toLocalTime())
                    .append(" ").append(group.headMessage().getLevel()).append(": ")
                    .append(message)
                    .append("\n");
        }
        return text.toString();
    }

    private String getApplicationName() {
        return applicationName.orElseGet(this::calculateApplicationName);
    }

    private String calculateApplicationName() {
        String hostname = "unknown host";
        try {
            hostname = Optional.ofNullable(System.getenv("HOSTNAME"))
                    .orElse(Optional.ofNullable(System.getenv("HTTP_HOST"))
                            .orElse(Optional.ofNullable(System.getenv("COMPUTERNAME"))
                                    .orElse(InetAddress.getLocalHost().getHostName())));
        } catch (UnknownHostException ignored) {
        }

        String username = System.getProperty("user.name");
        return username + "@" + hostname;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{smtpHost=" + props.getProperty("mail.smtp.host") + "}";
    }
}