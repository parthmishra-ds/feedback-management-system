-- ============================================================
-- College Feedback Management System : schema.sql
-- Engine : MySQL 8.0+ (InnoDB). Run once in MySQL Workbench.
-- Creates : database, 6 tables, 18 categories, 2 views.
-- ============================================================

CREATE DATABASE IF NOT EXISTS college_feedback
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE college_feedback;

-- ---------- Parent tables (depend on nothing) ----------

CREATE TABLE users (
  user_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  institutional_id VARCHAR(30)  NOT NULL,
  full_name        VARCHAR(100) NOT NULL,
  password_hash    VARCHAR(100) NOT NULL,              -- BCrypt hash, never plain text
  role             ENUM('STUDENT','FACULTY','STAFF','GUARD','HELPER','OTHER','ADMIN') NOT NULL,
  department       VARCHAR(100),
  email            VARCHAR(150) NULL,                  -- optional: where email alerts go
  phone            VARCHAR(20)  NULL,                  -- optional: international format, e.g. +919876543210
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_users_institutional_id UNIQUE (institutional_id)
) ENGINE=InnoDB;

CREATE TABLE feedback_categories (
  category_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_name VARCHAR(60) NOT NULL,
  description   VARCHAR(255),
  CONSTRAINT uq_category_name UNIQUE (category_name)
) ENGINE=InnoDB;

-- ---------- Activity tables (point back to the parents) ----------

CREATE TABLE feedback (
  feedback_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT   NOT NULL,
  category_id BIGINT   NOT NULL,
  rating      TINYINT  NOT NULL,
  comments    TEXT,
  status      ENUM('SUBMITTED','UNDER_REVIEW','ACTION_TAKEN','CLOSED')
              NOT NULL DEFAULT 'SUBMITTED',
  anonymous   BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id)     REFERENCES users(user_id),
  FOREIGN KEY (category_id) REFERENCES feedback_categories(category_id),
  CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

CREATE TABLE feedback_status_history (
  history_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
  feedback_id BIGINT NOT NULL,
  old_status  ENUM('SUBMITTED','UNDER_REVIEW','ACTION_TAKEN','CLOSED') NULL,  -- NULL on first row
  new_status  ENUM('SUBMITTED','UNDER_REVIEW','ACTION_TAKEN','CLOSED') NOT NULL,
  changed_by  BIGINT NULL,                 -- NULL = system / anonymous submitter
  note        VARCHAR(255),
  changed_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (feedback_id) REFERENCES feedback(feedback_id),
  FOREIGN KEY (changed_by)  REFERENCES users(user_id)
) ENGINE=InnoDB;

CREATE TABLE feedback_responses (
  response_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  feedback_id   BIGINT NOT NULL,
  admin_id      BIGINT NOT NULL,
  response_text TEXT   NOT NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (feedback_id) REFERENCES feedback(feedback_id),
  FOREIGN KEY (admin_id)    REFERENCES users(user_id),
  CHECK (CHAR_LENGTH(TRIM(response_text)) > 0)
) ENGINE=InnoDB;

-- In-app alerts (the bell). One row per person per event; email and SMS are sent from the same event.
CREATE TABLE notifications (
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

-- ---------- 18 pre-loaded categories ----------

INSERT INTO feedback_categories (category_name, description) VALUES
 ('Water',               'Drinking water, coolers and supply'),
 ('Mess Food',           'Quality, hygiene and variety of mess meals'),
 ('WiFi',                'Campus internet speed and coverage'),
 ('Library',             'Books, seating, timings and study environment'),
 ('Lift',                'Lift breakdowns and maintenance'),
 ('Classrooms',          'Seating, projectors, ventilation, boards'),
 ('Laboratories',        'Lab equipment, software and access'),
 ('Hostel Rooms',        'Rooms, furniture and repairs'),
 ('Washrooms',           'Cleanliness and plumbing'),
 ('Electricity',         'Power cuts, lighting, fans and AC'),
 ('Security',            'Gate security and campus safety'),
 ('Transport',           'College buses and pick-up/drop timings'),
 ('Sports and Gym',      'Grounds, equipment and timings'),
 ('Canteen',             'Snacks, pricing and service'),
 ('Teaching Quality',    'Course delivery and academic support'),
 ('Administration',      'Office response time and paperwork'),
 ('Cleanliness',         'Campus cleaning and waste handling'),
 ('Medical Centre',      'Health centre facilities and staff');

-- ---------- Views ----------

-- Management never sees who is behind an anonymous post.
CREATE VIEW v_admin_feedback AS
SELECT f.feedback_id,
       f.category_id,
       c.category_name,
       f.rating,
       f.comments,
       f.status,
       f.anonymous,
       f.created_at,
       CASE WHEN f.anonymous THEN NULL ELSE u.user_id          END AS submitter_id,
       CASE WHEN f.anonymous THEN 'Anonymous' ELSE u.full_name END AS submitter_name,
       CASE WHEN f.anonymous THEN NULL ELSE u.role             END AS submitter_role,
       CASE WHEN f.anonymous THEN NULL ELSE u.department       END AS submitter_department
FROM feedback f
JOIN feedback_categories c ON c.category_id = f.category_id
JOIN users u               ON u.user_id     = f.user_id;

-- Category summary used by the dashboard. LEFT JOIN keeps categories with no feedback.
CREATE VIEW v_category_summary AS
SELECT c.category_id,
       c.category_name,
       COUNT(f.feedback_id)          AS total,
       ROUND(AVG(f.rating), 2)       AS avg_rating
FROM feedback_categories c
LEFT JOIN feedback f ON f.category_id = c.category_id
GROUP BY c.category_id, c.category_name;
