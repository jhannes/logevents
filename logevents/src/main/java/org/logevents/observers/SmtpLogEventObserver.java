package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.config.LogEventConfigurationException;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventGroup;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Writes log events as asynchronous batches as email over SMTP.
 * <p>
 * Example configuration:
 * <pre>
 * observer.email=SmtpLogEventObserver
 * observer.email.threshold=WARN
 * observer.email.cooldownTime=PT10S
 * observer.email.maximumWaitTime=PT5M
 * observer.email.idleThreshold=PT5S
 * observer.email.suppressMarkers=BORING_MARKER
 * observer.email.requireMarker=MY_MARKER, MY_OTHER_MARKER
 * observer.email.markers.MY_MARKER.throttle=PT1M PT10M PT30M
 * observer.email.fromAddress=alerts@example.com
 * observer.email.recipients=alerts@example.com
 * observer.email.applicationName=MY APP
 * observer.email.smtpUsername=userName
 * observer.email.password=secret password
 * observer.email.host=smtp.example.com
 * </pre>
 */
public class SmtpLogEventObserver extends AbstractBatchingLogEventObserver {
    private final MessageFormatter messageFormatter;
    private final String nodeName;
    private String fromAddress;
    private String recipients;
    private String smtpUsername;
    private String smtpPassword;
    private Properties props;

    public SmtpLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public SmtpLogEventObserver(Configuration configuration) {
        this.fromAddress = configuration.getString("fromAddress");
        this.recipients = configuration.getString("recipients");
        this.smtpUsername = configuration.optionalString("username").orElse(fromAddress);
        this.smtpPassword = configuration.getString("password");
        this.messageFormatter = configuration.createInstanceWithDefault("messageFormatter", MessageFormatter.class);
        this.nodeName = configuration.getNodeName();

        configureFilter(configuration);

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

        configuration.checkForUnknownFields();
    }

    @Override
    protected void processBatch(LogEventBatch batch) {
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
