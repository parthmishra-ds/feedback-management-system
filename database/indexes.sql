-- ============================================================
-- Indexes + EXPLAIN comparison (roadmap: "Speed").
-- Step 1: run perf_seed.sql to load ~100,000 feedback rows.
-- Step 2: run the BEFORE block, note type / key / rows / cost.
-- Step 3: create the indexes, run the AFTER block, compare.
-- ============================================================
USE college_feedback;

-- ---------- BEFORE ----------
EXPLAIN SELECT feedback_id, rating, status, created_at
FROM feedback
WHERE status IN ('SUBMITTED','UNDER_REVIEW')
ORDER BY created_at ASC;

-- MySQL 8.0.18+ : actual timing, not just the estimate
EXPLAIN ANALYZE SELECT feedback_id, rating, status, created_at
FROM feedback
WHERE status IN ('SUBMITTED','UNDER_REVIEW')
ORDER BY created_at ASC;

-- ---------- CREATE ----------
-- (status, created_at): filter on status, already sorted by created_at inside each status.
CREATE INDEX idx_feedback_status_created ON feedback (status, created_at);
-- Dashboard per-category aggregation and "my feedback" lookups
CREATE INDEX idx_feedback_category_rating ON feedback (category_id, rating);
CREATE INDEX idx_feedback_user_created    ON feedback (user_id, created_at);
-- Audit trail and replies are always read per feedback, in time order
CREATE INDEX idx_history_feedback_time    ON feedback_status_history (feedback_id, changed_at);
CREATE INDEX idx_responses_feedback_time  ON feedback_responses (feedback_id, created_at);

ANALYZE TABLE feedback;

-- ---------- AFTER ----------
EXPLAIN SELECT feedback_id, rating, status, created_at
FROM feedback
WHERE status IN ('SUBMITTED','UNDER_REVIEW')
ORDER BY created_at ASC;

EXPLAIN ANALYZE SELECT feedback_id, rating, status, created_at
FROM feedback
WHERE status IN ('SUBMITTED','UNDER_REVIEW')
ORDER BY created_at ASC;

-- Note: with IN (...) on two statuses MySQL may still need a merge/filesort step.
-- Compare "rows examined" and "Using filesort" before/after and be ready to explain the result honestly.

-- To run this experiment again, drop the indexes first:
-- DROP INDEX idx_feedback_status_created ON feedback;
-- DROP INDEX idx_feedback_category_rating ON feedback;
-- DROP INDEX idx_feedback_user_created ON feedback;
-- DROP INDEX idx_history_feedback_time ON feedback_status_history;
-- DROP INDEX idx_responses_feedback_time ON feedback_responses;
