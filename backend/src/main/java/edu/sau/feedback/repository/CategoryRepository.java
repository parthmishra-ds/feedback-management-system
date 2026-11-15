package edu.sau.feedback.repository;

import edu.sau.feedback.entity.FeedbackCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<FeedbackCategory, Long> {
    List<FeedbackCategory> findAllByOrderByCategoryNameAsc();
}
