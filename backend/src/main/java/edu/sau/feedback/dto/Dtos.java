package edu.sau.feedback.dto;

import edu.sau.feedback.entity.Role;
import edu.sau.feedback.entity.Status;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

/** All request/response shapes of the REST API in one place (Java records). */
public final class Dtos {
    private Dtos() {}

    // ---------- requests ----------
    public record RegisterRequest(
            @NotBlank @Size(max = 30) String institutionalId,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @NotNull Role role,
            @Size(max = 100) String department) {}

    public record LoginRequest(@NotBlank String institutionalId, @NotBlank String password) {}

    public record FeedbackRequest(
            @NotNull Long categoryId,
            @NotNull @Min(value = 1, message = "Rating must be 1 to 5") @Max(value = 5, message = "Rating must be 1 to 5") Integer rating,
            @Size(max = 2000) String comments,
            boolean anonymous) {}

    public record StatusChangeRequest(@NotNull Status newStatus, @Size(max = 255) String note) {}

    public record ResponseRequest(@NotBlank @Size(max = 2000) String responseText) {}

    public record ContactRequest(
            @Email @Size(max = 150) String email,
            @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$", message = "Phone must be in international format, for example +919876543210") String phone) {}

    // ---------- responses ----------
    public record UserInfo(Long userId, String institutionalId, String fullName, Role role, String department) {}

    public record CategoryInfo(Long categoryId, String categoryName, String description) {}

    public record FeedbackSummary(Long feedbackId, String categoryName, int rating, String comments,
                                  Status status, boolean anonymous, LocalDateTime createdAt, String submitterName) {}

    public record HistoryItem(Status oldStatus, Status newStatus, String note, LocalDateTime changedAt, String byWhom) {}

    public record ResponseItem(Long responseId, String adminName, String responseText, LocalDateTime createdAt) {}

    public record FeedbackDetail(Long feedbackId, Long categoryId, String categoryName, int rating, String comments,
                                 Status status, boolean anonymous, LocalDateTime createdAt,
                                 String submitterName, String submitterRole, String submitterDepartment,
                                 List<HistoryItem> history, List<ResponseItem> responses) {}

    public record PageDto<T>(List<T> items, long total, int page, int size) {}

    public record CategoryStat(Long categoryId, String categoryName, long total, Double avgRating) {}

    public record Dashboard(long totalFeedback, Double averageRating, long pending,
                            java.util.Map<Status, Long> byStatus, List<CategoryStat> categories) {}

    public record ContactInfo(String email, String phone) {}

    public record NotificationItem(Long notificationId, Long feedbackId, String title, String message,
                                   boolean read, LocalDateTime createdAt) {}

    public record NotificationList(long unread, List<NotificationItem> items) {}
}
