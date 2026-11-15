package edu.sau.feedback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Read-only mapping of v_category_summary (GROUP BY aggregation done inside MySQL). */
@Entity
@Immutable
@Table(name = "v_category_summary")
@Getter
public class CategorySummaryView {
    @Id @Column(name = "category_id") private Long categoryId;
    @Column(name = "category_name") private String categoryName;
    private long total;
    @Column(name = "avg_rating") private Double avgRating;
}
