package edu.sau.feedback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Read-only mapping of the database view v_admin_feedback.
 * The view already replaces the submitter of an anonymous post with 'Anonymous',
 * so management code paths cannot leak identity even by mistake.
 */
@Entity
@Immutable
@Table(name = "v_admin_feedback")
@Getter
public class AdminFeedbackView {
    @Id @Column(name = "feedback_id") private Long feedbackId;
    @Column(name = "category_id") private Long categoryId;
    @Column(name = "category_name") private String categoryName;
    private int rating;
    private String comments;
    @Enumerated(EnumType.STRING) private Status status;
    private boolean anonymous;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "submitter_name") private String submitterName;
    @Column(name = "submitter_role") private String submitterRole;
    @Column(name = "submitter_department") private String submitterDepartment;
}
