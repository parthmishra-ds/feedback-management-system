-- ============================================================
-- Adds alerts (in-app, email, SMS) to a database created BEFORE this feature.
-- A fresh database made from the new schema.sql does not need this file.
-- Run once, as root:   mysql -u root -p < database/migration_notifications.sql
-- ============================================================
USE college_feedback;

ALTER TABLE users
  ADD COLUMN email VARCHAR(150) NULL,
  ADD COLUMN phone VARCHAR(20)  NULL;

CREATE TABLE IF NOT EXISTS notifications (
  notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  feedback_id     BIGINT       NULL,
  title           VARCHAR(150) NOT NULL,
  message         VARCHAR(500) NOT NULL,
  is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id)     REFERENCES users(user_id),
  FOREIGN KEY (feedback_id) REFERENCES feedback(feedback_id)
) ENGINE=InnoDB;

GRANT SELECT, INSERT, UPDATE ON college_feedback.notifications TO 'feedback_app'@'localhost';
-- the app may change only the two contact columns of a user, never the password hash or role
GRANT UPDATE (email, phone) ON college_feedback.users TO 'feedback_app'@'localhost';
FLUSH PRIVILEGES;
