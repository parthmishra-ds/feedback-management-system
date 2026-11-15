-- ============================================================
-- Performance seed: ~100,000 synthetic feedback rows (MySQL 8 recursive CTE).
-- Run AFTER schema.sql and AFTER the app has created at least one user (or the demo seeder has run).
-- Rows get categories and ratings spread evenly and statuses skewed like real life:
-- about 5% pending (SUBMITTED + UNDER_REVIEW), so the pending-list index has something to improve.
-- Dates are spread over the last 365 days.
-- To remove the synthetic rows afterwards:  DELETE FROM feedback WHERE comments = 'perf-seed';
--   (delete child rows in feedback_status_history first, see bottom of file)
-- ============================================================
USE college_feedback;

SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO feedback (user_id, category_id, rating, comments, status, anonymous, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT (SELECT MIN(user_id) FROM users),
       1 + (n MOD 18),
       1 + (n MOD 5),
       'perf-seed',
       CASE WHEN n MOD 100 < 2  THEN 'SUBMITTED'       -- about 2% waiting
            WHEN n MOD 100 < 5  THEN 'UNDER_REVIEW'    -- about 3% in review
            WHEN n MOD 100 < 15 THEN 'ACTION_TAKEN'    -- about 10%
            ELSE 'CLOSED' END,                         -- about 85% finished, like a real college
       (n MOD 7 = 0),
       NOW() - INTERVAL (n MOD 365) DAY - INTERVAL (n MOD 1440) MINUTE
FROM seq;

SELECT COUNT(*) AS total_rows FROM feedback;

-- Cleanup (optional)
-- DELETE h FROM feedback_status_history h JOIN feedback f ON f.feedback_id = h.feedback_id WHERE f.comments = 'perf-seed';
-- DELETE FROM feedback WHERE comments = 'perf-seed';
