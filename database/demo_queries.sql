-- ============================================================
-- The four queries from the presentation (slides 11-12), plus transaction demo.
-- ============================================================
USE college_feedback;

-- Q1: Join of 3 tables - every feedback with the person and category name, latest first
SELECT u.full_name, c.category_name, f.rating, f.status
FROM feedback f
JOIN users u ON f.user_id = u.user_id
JOIN feedback_categories c ON f.category_id = c.category_id
ORDER BY f.created_at DESC;

-- Q2: Aggregation - average rating and count per category (what the dashboard shows)
-- Slides sort DESC (best first). For "which area needs attention first" use ASC (worst first), as the dashboard does.
SELECT c.category_name,
       ROUND(AVG(f.rating), 2) AS avg_rating,
       COUNT(*) AS total
FROM feedback f
JOIN feedback_categories c ON f.category_id = c.category_id
GROUP BY c.category_name
ORDER BY avg_rating ASC;

-- Q3: Pending work list - oldest first
SELECT feedback_id, rating, comments, status, created_at
FROM feedback
WHERE status IN ('SUBMITTED','UNDER_REVIEW')
ORDER BY created_at ASC;

-- Q4: Audit trail of one feedback (change the id)
SELECT h.old_status, h.new_status, h.note, h.changed_at,
       COALESCE(u.full_name, 'System') AS by_whom
FROM feedback_status_history h
LEFT JOIN users u ON h.changed_by = u.user_id
WHERE h.feedback_id = 1
ORDER BY h.changed_at;

-- Anonymous protection: management view never exposes the submitter
SELECT feedback_id, category_name, rating, status, submitter_name
FROM v_admin_feedback
WHERE anonymous = TRUE
LIMIT 5;

-- Domain integrity demo (expect: Check constraint violated)
-- INSERT INTO feedback (user_id, category_id, rating) VALUES (1, 1, 9);

-- Referential integrity demo (expect: foreign key constraint fails)
-- INSERT INTO feedback (user_id, category_id, rating) VALUES (99999, 1, 3);

-- Atomicity demo: the app does these in ONE transaction. Run manually to show rollback.
-- START TRANSACTION;
--   UPDATE feedback SET status = 'UNDER_REVIEW' WHERE feedback_id = 1;
--   INSERT INTO feedback_status_history (feedback_id, old_status, new_status, changed_by, note)
--   VALUES (1, 'SUBMITTED', 'UNDER_REVIEW', 1, 'Picked up by admin');
-- ROLLBACK;   -- nothing persists; use COMMIT to keep both changes
