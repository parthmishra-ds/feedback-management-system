package edu.sau.feedback.service;

import edu.sau.feedback.dto.Dtos.*;
import edu.sau.feedback.entity.*;
import edu.sau.feedback.notify.NotificationCreated;
import edu.sau.feedback.repository.NotificationRepository;
import edu.sau.feedback.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Alerts. Each event writes the in-app row inside the caller's transaction (so it exists only if the action
 * itself committed) and publishes an event; email and SMS go out after the commit.
 *
 * Who is alerted:
 *   status change / management reply -> the person who sent the feedback (in-app + email; SMS only for Action taken and Closed)
 *   new feedback                     -> every management account (in-app; email and SMS only for a rating of 2 or less)
 * Alerts to management never contain the sender's name, so anonymous feedback stays anonymous.
 */
@Service
public class NotificationService {
    private static final int URGENT_RATING = 2;

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository notifications, UserRepository users, ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.users = users;
        this.events = events;
    }

    // ---------- events raised by FeedbackService (they run inside its transaction) ----------

    @Transactional
    public void newFeedback(Feedback f) {
        boolean urgent = f.getRating() <= URGENT_RATING;
        String cat = f.getCategory().getCategoryName();
        String title = (urgent ? "Low rating: " : "New feedback: ") + cat + ", " + f.getRating() + " of 5";
        String msg = "Open All feedback to read it and reply.";
        for (User admin : users.findByRole(Role.ADMIN)) create(admin, f.getFeedbackId(), title, msg, urgent, urgent);
    }

    @Transactional
    public void statusChanged(Feedback f, Status next, String note) {
        String title = "Your " + f.getCategory().getCategoryName() + " feedback is now " + label(next);
        String msg = note == null || note.isBlank() ? "Open My feedback to see the full history." : "Note from management: " + note.trim();
        boolean sms = next == Status.ACTION_TAKEN || next == Status.CLOSED;
        create(f.getUser(), f.getFeedbackId(), title, msg, true, sms);
    }

    @Transactional
    public void replied(Feedback f, String reply) {
        String title = "Management replied to your " + f.getCategory().getCategoryName() + " feedback";
        create(f.getUser(), f.getFeedbackId(), title, shorten(reply, 300), true, true);
    }

    private void create(User target, Long feedbackId, String title, String message, boolean email, boolean sms) {
        Notification n = new Notification();
        n.setUser(target);
        n.setFeedbackId(feedbackId);
        n.setTitle(shorten(title, 150));
        n.setMessage(shorten(message, 500));
        notifications.save(n);
        events.publishEvent(new NotificationCreated(target.getUserId(), target.getEmail(), target.getPhone(),
                n.getTitle(), n.getMessage(), email, sms));
    }

    // ---------- the bell (API) ----------

    @Transactional(readOnly = true)
    public NotificationList list(Long userId) {
        List<NotificationItem> items = notifications.latest(userId, PageRequest.of(0, 20)).stream()
                .map(n -> new NotificationItem(n.getNotificationId(), n.getFeedbackId(), n.getTitle(), n.getMessage(), n.isSeen(), n.getCreatedAt()))
                .toList();
        return new NotificationList(notifications.unread(userId), items);
    }

    @Transactional
    public NotificationList markRead(Long userId, Long id) {
        notifications.markSeen(id, userId);
        return list(userId);
    }

    @Transactional
    public NotificationList markAllRead(Long userId) {
        notifications.markAllSeen(userId);
        return list(userId);
    }

    // ---------- contact details ----------

    @Transactional(readOnly = true)
    public ContactInfo contact(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return new ContactInfo(u.getEmail(), u.getPhone());
    }

    @Transactional
    public ContactInfo updateContact(Long userId, ContactRequest req) {
        String email = blankToNull(req.email());
        String phone = blankToNull(req.phone());
        if (email != null) email = email.toLowerCase();
        if (users.updateContact(userId, email, phone) == 0) throw ApiException.notFound("User not found");
        return new ContactInfo(email, phone);
    }

    // ---------- helpers ----------

    private static String label(Status s) {
        String t = s.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
    private static String shorten(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 3) + "..."; }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
