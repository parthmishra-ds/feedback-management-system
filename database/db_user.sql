-- ============================================================
-- Limited database user for the application (roadmap: "Security and roles").
-- Run as root AFTER schema.sql. The app must NOT connect as root.
-- Change the password before real use.
-- ============================================================

CREATE USER IF NOT EXISTS 'feedback_app'@'localhost' IDENTIFIED BY 'ChangeMe#2026';

-- Read/write on data only. No DROP, ALTER, CREATE, GRANT.
GRANT SELECT, INSERT         ON college_feedback.users                   TO 'feedback_app'@'localhost';
GRANT SELECT                 ON college_feedback.feedback_categories     TO 'feedback_app'@'localhost';
GRANT SELECT, INSERT, UPDATE ON college_feedback.feedback                TO 'feedback_app'@'localhost';
GRANT SELECT, INSERT         ON college_feedback.feedback_status_history TO 'feedback_app'@'localhost';
GRANT SELECT, INSERT         ON college_feedback.feedback_responses      TO 'feedback_app'@'localhost';
GRANT SELECT, INSERT, UPDATE ON college_feedback.notifications          TO 'feedback_app'@'localhost';
GRANT UPDATE (email, phone)  ON college_feedback.users                   TO 'feedback_app'@'localhost';
GRANT SELECT                 ON college_feedback.v_admin_feedback        TO 'feedback_app'@'localhost';
GRANT SELECT                 ON college_feedback.v_category_summary      TO 'feedback_app'@'localhost';

FLUSH PRIVILEGES;

-- History and replies are append-only (no UPDATE/DELETE granted) so the audit trail cannot be edited by the app.
