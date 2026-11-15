package edu.sau.feedback.repository;

import edu.sau.feedback.entity.AdminFeedbackView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdminFeedbackViewRepository
        extends JpaRepository<AdminFeedbackView, Long>, JpaSpecificationExecutor<AdminFeedbackView> {
}
