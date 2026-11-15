package edu.sau.feedback.repository;

import edu.sau.feedback.entity.CategorySummaryView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorySummaryRepository extends JpaRepository<CategorySummaryView, Long> {
}
