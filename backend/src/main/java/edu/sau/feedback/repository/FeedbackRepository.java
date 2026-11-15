package edu.sau.feedback.repository;

import edu.sau.feedback.entity.Feedback;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @Query("select f from Feedback f join fetch f.category where f.user.userId = :userId order by f.createdAt desc, f.feedbackId desc")
    List<Feedback> findMine(@Param("userId") Long userId);

    @Query("select f from Feedback f join fetch f.category where f.feedbackId = :id")
    Optional<Feedback> findWithCategory(@Param("id") Long id);

    /** SELECT ... FOR UPDATE: two admins cannot change the same feedback at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Feedback f where f.feedbackId = :id")
    Optional<Feedback> lockById(@Param("id") Long id);

    @Query("select avg(f.rating) from Feedback f")
    Double averageRating();

    @Query("select f.status, count(f) from Feedback f group by f.status")
    List<Object[]> countByStatus();
}
