package edu.sau.feedback.notify;

/** Published inside the database transaction; email and SMS are sent only after that transaction commits. */
public record NotificationCreated(Long userId, String email, String phone, String subject, String body,
                                  boolean sendEmail, boolean sendSms) {}
