package edu.sau.feedback.repository;

import edu.sau.feedback.entity.FeedbackResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResponseRepository extends JpaRepository<FeedbackResponse, Long> {
    @Query("select r from FeedbackResponse r join fetch r.admin where r.feedback.feedbackId = :id order by r.createdAt asc, r.responseId asc")
    List<FeedbackResponse> findReplies(@Param("id") Long feedbackId);
}
