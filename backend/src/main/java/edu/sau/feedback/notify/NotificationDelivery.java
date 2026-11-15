package edu.sau.feedback.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs after the transaction commits and on a background thread. A failing mail server or SMS provider
 * is logged and ignored: it can never roll back or delay the feedback action that caused the alert.
 */
@Component
public class NotificationDelivery {
    private static final Logger log = LoggerFactory.getLogger(NotificationDelivery.class);

    private final EmailSender email;
    private final SmsSender sms;

    public NotificationDelivery(EmailSender email, SmsSender sms) {
        this.email = email;
        this.sms = sms;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(NotificationCreated e) {
        if (e.sendEmail() && e.email() != null) {
            try { email.send(e.email(), e.subject(), e.body()); }
            catch (Exception ex) { log.warn("Email to user {} failed: {}", e.userId(), ex.getMessage()); }
        }
        if (e.sendSms() && e.phone() != null) {
            try { sms.send(e.phone(), e.subject() + ". " + e.body()); }
            catch (Exception ex) { log.warn("SMS to user {} failed: {}", e.userId(), ex.getMessage()); }
        }
    }
}
