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
    private final String smtpPort;
    private String fromAddress;
    private String recipients;
    private Optional<String> applicationName;
    private String smtpHost;
    private String smtpUsername;
    private String smtpPassword;

    public SmtpLogEventBatchProcessor(Configuration configuration) {
        this.fromAddress = configuration.getString("fromAddress");
        this.recipients = configuration.getString("recipients");
        this.applicationName = configuration.optionalString("applicationName");
        this.smtpHost = configuration.getString("host");
        this.smtpPort = configuration.optionalString("port").orElse("587");
        this.smtpUsername = configuration.optionalString("username").orElse(fromAddress);
        this.smtpPassword = configuration.getString("password");

        try {
            Class.forName("com.sun.mail.util.MailLogger");
        } catch (ClassNotFoundException ignored) {
            throw new LogEventConfigurationException("You have to include com.sun.mail:javax.mail in your classpath to use " + getClass().getName());
        }


    }

    @Override
    public void processBatch(LogEventBatch batch) {
        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);

        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.user", smtpUsername);
        props.put("mail.smtp.password", smtpPassword);
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(fromAddress);
            message.setRecipients(Message.RecipientType.TO, recipients);
            message.setSubject("[" + getApplicationName() + "] " + batch.firstHighestLevelLogEventGroup().headMessage().formatMessage());
            message.setText(formatMessageBatch(batch));

            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
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
}
