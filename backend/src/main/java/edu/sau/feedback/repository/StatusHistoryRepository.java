package edu.sau.feedback.repository;

import edu.sau.feedback.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    @Query("select h from StatusHistory h left join fetch h.changedBy where h.feedback.feedbackId = :id order by h.changedAt asc, h.historyId asc")
    List<StatusHistory> findTrail(@Param("id") Long feedbackId);
}
