package edu.sau.feedback.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends plain-text email through the configured SMTP server. When disabled it only logs, so demos never send mail by accident. */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mail;
    private final boolean enabled;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mail,
                       @Value("${app.notify.email.enabled:false}") boolean enabled,
                       @Value("${app.notify.email.from:no-reply@feedback.local}") String from) {
        this.mail = mail;
        this.enabled = enabled;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender sender = enabled ? mail.getIfAvailable() : null;
        if (sender == null) {
            log.info("[EMAIL not sent: {}] to={} subject=\"{}\"", enabled ? "MAIL_HOST is not set" : "NOTIFY_EMAIL is off", to, subject);
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject(subject);
        m.setText(body + "\n\nThis is an automated message from the College Feedback Management System.");
        sender.send(m);
        log.info("[EMAIL sent] to={} subject=\"{}\"", to, subject);
    }
}
