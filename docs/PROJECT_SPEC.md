# College Feedback Management System: Project Specification

Team: Ravikant Shukla, Parth Mishra, Saksham Shrivastava, Kshitij Kumar · MCA, South Asian University, New Delhi
Course: Database Systems · Stack: Spring Boot 3 · MySQL 8 (InnoDB) · HTML/CSS/JavaScript

Everything below is derived from the 15-slide midsem presentation. Where the slides were inconsistent, this document says so (see **Slide gaps resolved**, end of section 1) so the demo and the slides do not contradict each other.

---

## 1. PROJECT OVERVIEW

### System name, vision, objectives

**College Feedback Management System (CFMS)**: one place to collect feedback from everyone on campus, track what happens to it, and show management the full picture.

| # | Objective | How it is measured |
|---|---|---|
| O1 | Single record of all feedback | Every feedback is a row in `feedback`; none lives in WhatsApp or paper |
| O2 | Follow-up until closed | Each feedback moves SUBMITTED → UNDER_REVIEW → ACTION_TAKEN → CLOSED; every change is a row in `feedback_status_history` |
| O3 | Overall picture for management | Dashboard: total feedback, average rating, category-wise summary, pending count |
| O4 | Safe handling of identities and passwords | BCrypt hashes only; anonymous posts hidden from management by a database view |
| O5 | Correct data by construction | PK/FK/UNIQUE/NOT NULL/CHECK/ENUM enforced inside MySQL, schema in 3NF |

### Target users and stakeholders

| Stakeholder | Role in system | What they do |
|---|---|---|
| Student, Faculty, Staff, Guard, Helper, Other | `STUDENT`, `FACULTY`, `STAFF`, `GUARD`, `HELPER`, `OTHER` | Register with college ID, log in, pick one of 18 categories, give 1 to 5 stars and a comment, optionally post anonymously, see own feedback and replies |
| Management | `ADMIN` | See all feedback, open the dashboard, change status, write replies |
| Course instructor | Evaluator | Judges database design, SQL, transactions, integrity, security |
| Team (4 members) | Developers | Build and present |

### Problem statement

1. **Complaints are scattered**: WhatsApp groups, paper, verbal. No single record.
2. **No follow-up**: nobody can tell whether a complaint was seen, acted on or closed.
3. **No overall picture**: management cannot see which area (water, mess, WiFi…) is rated worst.

### Scope and deliverables

**In scope:** web app (3 tiers), 5-table MySQL database, 18 pre-loaded categories, registration/login, feedback submission, status workflow with audit trail, replies, management dashboard, anonymous protection, indexing with EXPLAIN comparison, limited DB user.

**Out of scope (stated on slide 2):** email or SMS alerts, a mobile app, charts. The dashboard therefore uses numbers and a plain table (with a small inline bar made from CSS, not a chart library).

| Deliverable | File / location |
|---|---|
| Database scripts | `database/schema.sql`, `db_user.sql`, `indexes.sql`, `perf_seed.sql`, `demo_queries.sql` |
| Backend (REST API) | `backend/src/main/java/edu/sau/feedback/**` |
| Frontend | `backend/src/main/resources/static/` (served by Spring Boot, so one process to run) |
| Automated tests | `backend/src/test/java/edu/sau/feedback/FeedbackFlowTest.java` |
| Documentation | this file + `README.md` |

### Slide gaps resolved (be ready to answer if asked)

| Slide says | Problem | What the project does |
|---|---|---|
| ER diagram (slide 4) shows no `note`, `changed_at`, `created_at`, `comments` | The queries on slides 10 to 12 use all four | They are real columns in `schema.sql`. Update the ER slide to show them. |
| Slide 11 aggregation ends `ORDER BY avg_rating DESC` and says it "tells which area needs attention first" | DESC lists the *best* area first | Dashboard sorts **ascending** (worst first). `demo_queries.sql` uses ASC and says why. |
| `feedback.status` has no `NOT NULL` on slide 10 | A NULL status would break the workflow | `NOT NULL DEFAULT 'SUBMITTED'` |
| Slide 11 inner-joins feedback to categories | Categories with zero feedback disappear from the summary | The dashboard view uses `LEFT JOIN`, so all 18 categories show |
| Slide 3 says "post anonymously" but never says who is hidden from whom | Roadmap (slide 14) asks for a view that hides the user from management | Implemented as `v_admin_feedback`; the API reads management lists from this view only |
| Slide 14: "set up the admin account properly" | The admin cannot self-register | Admin is created at startup from environment variables (`ADMIN_ID`, `ADMIN_PASSWORD`), never hard-coded |

---

## 2. FEATURE BREAKDOWN

Priority: **P0** = must-have for MVP · **P1** = should-have · **P2** = nice-to-have.

### Core features (P0)

| ID | Feature | User story | Acceptance criteria |
|---|---|---|---|
| F1 | Registration | As a **member of the college**, I want to register with my college ID so that I can give feedback. | Unique college ID (duplicate → 409). Password ≥ 8 chars. Password stored as BCrypt hash (`$2…`), never plain. Role limited to Student/Faculty/Staff/Guard/Helper/Other; `ADMIN` self-registration → 403. |
| F2 | Login / session | As a **user**, I want to log in so that only I can see my feedback. | Correct credentials create an HTTP session. Wrong ID or password gives the same 401 message. Unauthenticated `/api/**` → 401. Logout ends the session. |
| F3 | Submit feedback | As a **user**, I want to pick a category, give 1 to 5 stars and a comment so that management knows my experience. | One of 18 categories (invalid → 400). Rating 1 to 5 (else 400, and the DB `CHECK` is a second guard). Creates **one** row in `feedback` and **one** first row in `feedback_status_history` in the **same transaction**. New status = `SUBMITTED`. |
| F4 | Anonymous posting | As a **user**, I want to post anonymously so that I can speak freely. | With the box ticked, `anonymous = TRUE`. Management screens and API show "Anonymous" and no name, role or department. The submitter still sees it under My feedback. |
| F5 | My feedback and replies | As a **user**, I want to see my own feedback, its status and the replies so that I know what happened. | List shows only my rows. Detail shows status history and replies. Opening someone else's feedback → 404. |
| F6 | Management dashboard | As **management**, I want totals, average rating and a category summary so that I see which area is worst. | Shows total, average (2 decimals), pending (SUBMITTED + UNDER_REVIEW), closed. Category table sorted worst-rated first, unrated last. Numbers equal the Workbench aggregation query. Only `ADMIN` can open it (others → 403). |
| F7 | Change status | As **management**, I want to move a feedback through the workflow so that complaints are tracked until closed. | Moves forward only (backward or same state → 409). Updates `feedback` and inserts a history row (old status, new status, who, when, note) in one transaction. Row is locked (`SELECT … FOR UPDATE`) so two admins cannot collide. |
| F8 | Reply | As **management**, I want to write a reply so that the user hears back. | Non-empty text (blank → 400, DB `CHECK` second guard). Stored in `feedback_responses` with admin id and time. Visible to the owner. |
| F9 | Audit trail | As **management**, I want every status change saved with who and when so that nothing is silently altered. | History rows are append-only (the DB user has no UPDATE/DELETE on them). Shown as a timeline. |

### Secondary features (P1)

| ID | Feature | User story | Acceptance criteria |
|---|---|---|---|
| F10 | Indexes + EXPLAIN | As a **developer**, I want indexes on the columns the dashboard filters by so that it stays fast. | `idx_feedback_status_created (status, created_at)` and four more in `indexes.sql`. `EXPLAIN` / `EXPLAIN ANALYZE` captured before and after on 100,000 rows from `perf_seed.sql`. |
| F11 | Limited DB user | As the **owner of the data**, I want the app to use an account that cannot DROP or ALTER so that a bug or attack is contained. | `feedback_app` has only SELECT/INSERT/UPDATE on the tables it needs. No root account in `application.properties`. |
| F12 | Anonymous protection view | As an **anonymous poster**, I want management never to see who I am so that I am safe. | `v_admin_feedback` masks name, role, department. Even the first audit row of an anonymous post has `changed_by = NULL`. Test `anonymousPostIsHiddenFromManagement` proves it. |
| F13 | Filters and pending work list | As **management**, I want to filter by status/category and see unfinished items oldest-first so that old complaints are not forgotten. | `GET /api/admin/feedback?status=&categoryId=&pending=true`. Pending list sorted `created_at ASC`. Paged (max 100 per page). |
| F14 | Demo data | As a **presenter**, I want realistic sample data so that the dashboard has something to show. | `--spring.profiles.active=demo` loads 7 users, 24 feedback, some moved through the lifecycle with replies. Idempotent. |

### Nice-to-have (P2, time permitting)

| ID | Feature | Note |
|---|---|---|
| F15 | Email/SMS alerts | **Excluded by slide 2.** |
| F16 | Mobile app | **Excluded by slide 2.** |
| F17 | Charts | **Excluded by slide 2.** Add only after approval. |
| F18 | Login rate limiting / lockout | Security hardening |
| F19 | CSV export of feedback | Reporting |
| F20 | Edit/withdraw own feedback within 10 minutes | Needs an extra status or a soft-delete column |
| F21 | Password reset | Needs email (also excluded) |

### Feature dependencies

```
F1 Registration ─► F2 Login ─► F3 Submit ─► F5 My feedback
                      │            │
                      │            ├─► F4 Anonymous ─► F12 Anonymous view
                      │            └─► F9 Audit trail (first row written by F3)
                      └─► F6 Dashboard ─► F13 Filters
                           F7 Change status ─► F9 Audit trail
                           F8 Reply
F10 Indexes needs data from F14/perf_seed.  F11 Limited user is independent (DB only).
```

---

## 3. TECHNICAL ARCHITECTURE

### System design: three tiers (slide 9)

```
┌───────────────────────┐  HTTP + JSON  ┌────────────────────────────┐  JDBC + JPA  ┌──────────────────────────┐
│ TIER 1 · PRESENTATION │ ────────────► │ TIER 2 · APPLICATION       │ ───────────► │ TIER 3 · DATA            │
│ Browser               │               │ Spring Boot 3 REST API     │              │ MySQL 8 (InnoDB)         │
│ HTML, CSS, JavaScript │ ◄──────────── │  Security filter (session) │ ◄─────────── │ DB college_feedback      │
│ Login · Feedback form │               │  Controllers: Auth,        │              │ 5 tables + 2 views       │
│ My feedback · Admin   │               │   Feedback, Admin          │              │ port 3306                │
│ dashboard             │               │  Services (transactions)   │              │ user: feedback_app       │
└───────────────────────┘               │  Spring Data JPA repos     │              └──────────────────────────┘
                                        └────────────────────────────┘
```

Rules from the slides, and where they are enforced:

| Rule | Enforced in |
|---|---|
| The browser never touches the database | Only `fetch('/api/...')` in `app.js`; MySQL is not exposed to the page |
| Every request is checked against the login session first | `SecurityConfig` filter chain runs before any controller |
| Controllers never write raw queries | Controllers call services; services call Spring Data JPA repositories |
| JDBC address is in `application.properties` | `spring.datasource.url` (overridable by `DB_URL`) |

### Technology stack and justification

| Layer | Choice | Why |
|---|---|---|
| Database | **MySQL 8.0, InnoDB** | Data is connected through foreign keys, so relational beats a document store; InnoDB gives ACID transactions, FKs and `CHECK`; free; Workbench for demo (slide 8) |
| Backend | **Spring Boot 3.3, Java 17+** | Industry standard; Spring Data JPA and Hibernate support MySQL with little setup (slide 8) |
| Security | **Spring Security + BCrypt** | Session login, role checks (`hasRole('ADMIN')`), password hashing (slide 3) |
| Frontend | **HTML + CSS + vanilla JavaScript** | No build step, nothing to install, easy to explain; served from Spring Boot |
| Tests | **JUnit 5, MockMvc, H2 (MySQL mode)** | Runs the real `schema.sql` in memory, no MySQL needed for tests |
| Build | **Maven** | One command to test and run |

### Database schema (5 entities, 3NF)

```
users 1───N feedback N───1 feedback_categories
  │            │ 1
  │            ├──N feedback_status_history   (changed_by → users, nullable)
  │            └──N feedback_responses        (admin_id → users)
  └─ 1──N feedback_status_history.changed_by,  1──N feedback_responses.admin_id
```

| Table | PK | Foreign keys | Rules |
|---|---|---|---|
| `users` | `user_id` | none | `institutional_id` UNIQUE; `full_name`, `password_hash`, `role` NOT NULL; `role` ENUM(STUDENT, FACULTY, STAFF, GUARD, HELPER, OTHER, ADMIN) |
| `feedback_categories` | `category_id` | none | `category_name` UNIQUE |
| `feedback` | `feedback_id` | `user_id → users`, `category_id → feedback_categories` | `CHECK (rating BETWEEN 1 AND 5)`; `status` ENUM(SUBMITTED, UNDER_REVIEW, ACTION_TAKEN, CLOSED) NOT NULL; `anonymous` BOOLEAN |
| `feedback_status_history` | `history_id` | `feedback_id → feedback`, `changed_by → users` (NULL = system) | `new_status` NOT NULL; `old_status` NULL on the first row; `note`, `changed_at` |
| `feedback_responses` | `response_id` | `feedback_id → feedback`, `admin_id → users` | `response_text` NOT NULL and not blank |

Views: `v_admin_feedback` (masks anonymous submitters), `v_category_summary` (per-category count and average, LEFT JOIN).

Integrity constraints (slide 6) and where to show them:

| Type | Mechanism | Demo |
|---|---|---|
| Entity | `AUTO_INCREMENT PRIMARY KEY` on every table | Insert two rows, ids differ |
| Referential | Foreign keys | `INSERT INTO feedback … user_id=99999` fails |
| Domain | `CHECK`, `ENUM` | `INSERT … rating=9` fails |
| Unique / NOT NULL | `UNIQUE`, `NOT NULL` | Register same college ID twice → 409 |

Normalization (slide 7): 1NF one value per cell; 2NF every table has a single-column key; 3NF category name lives only in `feedback_categories`, user name and department only in `users`. Renaming "Mess Food" changes one row.

### API / module structure

| Method | Endpoint | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create account (BCrypt) |
| POST | `/api/auth/login` | public | Start session |
| POST | `/api/auth/logout` | logged in | End session |
| GET | `/api/auth/me` | logged in | Current user (used on page load) |
| GET | `/api/categories` | logged in | 18 categories |
| POST | `/api/feedback` | logged in | Submit feedback (transaction 1) |
| GET | `/api/feedback/mine` | logged in | My feedback list |
| GET | `/api/feedback/{id}` | owner | Detail + history + replies |
| GET | `/api/admin/dashboard` | ADMIN | Totals, average, status counts, category summary |
| GET | `/api/admin/feedback` | ADMIN | List with `status`, `categoryId`, `pending`, `page`, `size` |
| GET | `/api/admin/feedback/{id}` | ADMIN | Detail (anonymous masked) |
| PATCH | `/api/admin/feedback/{id}/status` | ADMIN | Change status (transaction 2) |
| POST | `/api/admin/feedback/{id}/responses` | ADMIN | Add reply (transaction 3) |

Packages: `controller` (HTTP only) → `service` (business rules, `@Transactional`) → `repository` (Spring Data JPA) → `entity` (JPA mappings). `dto` holds request/response records, `security` the session principal, `config` security setup and startup seeders.

### Authentication and authorization

1. `POST /api/auth/login` → `AuthenticationManager` loads the user and compares the password with **BCrypt**.
2. On success the login is stored in the server-side **HTTP session** (cookie `JSESSIONID`, `HttpOnly`, `SameSite=Lax`, 30 min). The session id is rotated on login (session-fixation defence).
3. `SecurityConfig`: `/api/auth/register|login` public; `/api/admin/**` needs `ROLE_ADMIN`; every other `/api/**` needs a login; static files are public.
4. Ownership rule in the service: a user can open only their own feedback (others get 404, which does not reveal that the id exists).
5. Management reads feedback only through `v_admin_feedback`.

Trade-offs to discuss: CSRF protection is switched off because the API accepts only JSON and the cookie is `SameSite=Lax`; a production system with cross-site embedding would add CSRF tokens. Session login (not JWT) was chosen because the slides say "checks the login session", it is simpler, and logout really ends the session.

---

## 4. IMPLEMENTATION ROADMAP

The presentation shows the database layer already done at midsem (slides 4 to 12). The 8-week plan below is written so that finished work is visible and the remaining slide-14 items are scheduled. "Done" means present in this repository; it does not mean tested on your machine (see README, Verification status).

### Phase 1: Core (Weeks 1 to 3)

| Task | Deliverable | Status |
|---|---|---|
| Requirements, roles, 3 main transactions | Slides 2 to 3 | Done |
| ER diagram, relational schema, constraints, 3NF | Slides 4 to 7 | Done |
| `schema.sql` with 5 tables + 18 categories | `database/schema.sql` | Done |
| Spring Boot project, JPA entities, repositories | `backend/…/entity`, `repository` | Done |
| Registration, login, BCrypt, session | `AuthController`, `SecurityConfig` | Done |
| Submit feedback (transaction 1) | `FeedbackService.submit` | Done |

### Phase 2: Secondary (Weeks 4 to 6)

| Task | Deliverable | Status |
|---|---|---|
| Status change + audit (transaction 2), reply (transaction 3) | `changeStatus`, `addResponse` | Done |
| Dashboard + filters + pending list | `AdminController`, `v_category_summary` | Done |
| Frontend: login, form, my feedback, admin | `static/` | Done |
| Indexes + EXPLAIN comparison | `indexes.sql`, `perf_seed.sql` | **Run and record results** |
| Limited DB user | `db_user.sql` | **Run and test the app with it** |
| Anonymous protection view | `v_admin_feedback` | Done |

### Phase 3: Polish and testing (Weeks 7 to 8)

| Task | Deliverable | Status |
|---|---|---|
| Replace placeholder college name, set up admin account | `COLLEGE_NAME` in `app.js`, `ADMIN_PASSWORD` env var | Done (verify name) |
| Automated end-to-end tests | `FeedbackFlowTest` (9 tests) | **Run `mvn test`** |
| Test every API against real MySQL | Manual checklist in section 6 | **To do** |
| Final report and screenshots | PDF | **To do** |
| Rehearse the 5-step demo | Section 6 | **To do** |

### Resource requirements

| Resource | Detail |
|---|---|
| People | 4 members: database (schema, queries, EXPLAIN), backend (services, security), frontend, QA/documentation/demo. Rotate so everyone can explain every part. |
| Software | JDK 17+, Maven 3.9+, MySQL 8 + Workbench, any browser, VS Code or IntelliJ |
| Hardware | One normal Windows laptop (slide 8) |
| Time | About 6 to 8 hours per person per week |

---

## 5. PROTOTYPE STRUCTURE

### Directory layout

```
college-feedback-system/
├── README.md
├── docs/PROJECT_SPEC.md
├── database/
│   ├── schema.sql          5 tables, 18 categories, 2 views
│   ├── db_user.sql         limited user feedback_app
│   ├── indexes.sql         EXPLAIN before / indexes / EXPLAIN after
│   ├── perf_seed.sql       100,000 synthetic rows
│   └── demo_queries.sql    the 4 slide queries + integrity demos
└── backend/
    ├── pom.xml
    └── src/
        ├── main/java/edu/sau/feedback/
        │   ├── FeedbackApplication.java
        │   ├── config/      SecurityConfig, AdminBootstrap, DemoDataSeeder
        │   ├── security/    AppUserDetails, AppUserDetailsService
        │   ├── controller/  AuthController, FeedbackController, AdminController, ApiExceptionHandler
        │   ├── service/     AuthService, FeedbackService, ApiException
        │   ├── repository/  7 Spring Data JPA repositories
        │   ├── entity/      User, FeedbackCategory, Feedback, StatusHistory, FeedbackResponse,
        │   │                AdminFeedbackView, CategorySummaryView, Role, Status
        │   └── dto/         Dtos (request/response records)
        ├── main/resources/
        │   ├── application.properties, application-demo.properties
        │   └── static/      index.html, css/theme.css, js/app.js
        └── test/            FeedbackFlowTest, H2SchemaConfig, application-test.properties
```

### Key modules and responsibilities

| Module | Responsibility |
|---|---|
| `schema.sql` | Single source of truth for the database. Tests run this same file. |
| `FeedbackService` | The three transactions, ownership rule, forward-only status rule, dashboard numbers |
| `SecurityConfig` + `AuthController` | Session login, BCrypt, role-based access |
| `v_admin_feedback` + `AdminFeedbackView` | Makes it impossible for management code to read an anonymous user's identity |
| `app.js` | Four screens (auth, user, my feedback, admin), talks only to the API |

### Sample implementation snippets

**1. The feedback table (slide 10, hardened):** `database/schema.sql`

```sql
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
```

**2. Anonymous protection view:** `database/schema.sql`

```sql
CREATE VIEW v_admin_feedback AS
SELECT f.feedback_id, c.category_name, f.rating, f.comments, f.status, f.anonymous, f.created_at,
       CASE WHEN f.anonymous THEN 'Anonymous' ELSE u.full_name END AS submitter_name
FROM feedback f
JOIN feedback_categories c ON c.category_id = f.category_id
JOIN users u               ON u.user_id     = f.user_id;
-- (the real view also masks role, department and submitter id)
```

**3. Transaction 2, change status:** `FeedbackService.changeStatus`

```java
@Transactional
public FeedbackDetail changeStatus(Long adminId, Long feedbackId, StatusChangeRequest req) {
    Feedback f = feedbacks.lockById(feedbackId)            // SELECT ... FOR UPDATE
            .orElseThrow(() -> ApiException.notFound("Feedback not found"));
    Status old = f.getStatus(), next = req.newStatus();
    if (old == next)            throw ApiException.conflict("Feedback is already " + old);
    if (!old.canMoveTo(next))   throw ApiException.conflict("Status cannot move backwards");

    f.setStatus(next);                                      // 1) update feedback
    feedbacks.saveAndFlush(f);
    StatusHistory h = new StatusHistory();                  // 2) append audit row
    h.setFeedback(f); h.setOldStatus(old); h.setNewStatus(next);
    h.setChangedBy(users.findById(adminId).orElseThrow()); h.setNote(req.note());
    history.saveAndFlush(h);                                // both commit, or neither does
    return adminDetail(feedbackId);
}
```

**4. Security rules:** `SecurityConfig`

```java
.authorizeHttpRequests(a -> a
    .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/**").authenticated()
    .anyRequest().permitAll())                             // static frontend files
```

**5. Dashboard aggregation (slide 11, as used by the view):**

```sql
SELECT c.category_name, COUNT(f.feedback_id) AS total, ROUND(AVG(f.rating), 2) AS avg_rating
FROM feedback_categories c LEFT JOIN feedback f ON f.category_id = c.category_id
GROUP BY c.category_id, c.category_name
ORDER BY avg_rating ASC;      -- worst first
```

### Configuration

`application.properties` reads everything sensitive from environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/college_feedback?...` | JDBC address |
| `DB_USER` / `DB_PASSWORD` | `feedback_app` / `ChangeMe#2026` | Limited DB account from `db_user.sql` |
| `ADMIN_ID` | `ADMIN001` | Management login ID |
| `ADMIN_PASSWORD` | *(none)* | Required (8+ chars). With the `demo` profile it defaults to `Admin@123`. |
| `PORT` | `8080` | HTTP port |

### Theme (design tokens in `css/theme.css`)

Colours follow the SAU Mess website (navy blue, gold accent, cream notice strip). Layout and fonts are our own.

| Role | Hex | Use |
|---|---|---|
| Primary (nav blue) | `#1F4E9E` | Top bar, buttons, links, active states |
| Primary hover | `#173F80` | Hover |
| Navy | `#14284B` | Login hero panel, footer |
| Gold accent | `#C9A227` | Thin lines under the top bar and headings |
| Background | `#F4F7FC` | Light blue-white page canvas |
| Surface | `#FFFFFF` | Cards |
| Text | `#14213D` | Headings, body |
| Muted text | `#5B6B86` | Labels, hints |
| Notice cream | `#FFF8E1` | "Needs attention" banner, pending badges |
| Success | `#2E7D32` | Closed (resolved) |
| Warning | `#C77700` | Submitted and under review (pending), stars |
| Error | `#DC2626`-family `#C62828` | Errors, ratings below 2.5 |

The footer states that this is a student project and not an official SAU website.

---

## 6. DEMONSTRATION PLAN

Follows slide 13 (5 steps). About 8 minutes.

| # | Scenario | Actions | Expected behaviour | Say this |
|---|---|---|---|---|
| 1 | **Look at the database** | Workbench: expand `college_feedback` | 5 tables, 2 views. `SELECT COUNT(*) FROM feedback_categories` → **18** | "Schema is one script. Constraints live in the database, not only in Java." |
| 2 | **Register and log in** | Register `STU2026099`, role Student. Then `SELECT user_id, institutional_id, password_hash FROM users` | New row; `password_hash` starts with `$2` (BCrypt), not the password | "Plain passwords are never stored. Hash is salted, so two equal passwords give different hashes." |
| 3 | **Submit feedback (Create)** | Give 2 stars to WiFi with a comment. Then in Workbench: `SELECT * FROM feedback ORDER BY feedback_id DESC LIMIT 1;` and `SELECT * FROM feedback_status_history ORDER BY history_id DESC LIMIT 1;` | One new `feedback` row (status SUBMITTED) and one history row (`old_status` NULL, `new_status` SUBMITTED) | "One transaction, two inserts. If the second fails, the first is rolled back." |
| 3b | **Anonymous** (30 s) | Submit one more with Anonymous ticked. Log in as admin, open it | Admin sees "Anonymous", no name. `SELECT * FROM v_admin_feedback WHERE anonymous` shows the same | "Management reads through a view that masks the user." |
| 4 | **Management (Read + Update)** | Log out. Log in `ADMIN001`. Dashboard → All feedback → open the new item → Move to *Under review* with a note → write a reply. Then log in as the student and open My feedback | Status badge changes, timeline shows 2 rows with admin name and time, reply visible to the student. Moving backwards shows an error | "Every change is saved with who and when. The audit query on slide 12 reads exactly this." |
| 5 | **Query live** | Run the aggregation (`demo_queries.sql`, Q2) | Result equals the dashboard category table, same numbers | "Dashboard is just this GROUP BY, computed by MySQL." |

### Bonus scenarios if time allows

| Scenario | Command | Expected |
|---|---|---|
| Domain integrity | `INSERT INTO feedback (user_id, category_id, rating) VALUES (1,1,9);` | Check constraint violated |
| Referential integrity | `... VALUES (99999,1,3);` | Foreign key constraint fails |
| Atomicity | The commented `START TRANSACTION … ROLLBACK` block in `demo_queries.sql` | Nothing persists after ROLLBACK |
| Indexes | `indexes.sql` after `perf_seed.sql` | Compare `type`, `key`, `rows` before and after |
| Least privilege | Connect as `feedback_app`, run `DROP TABLE feedback;` | Access denied |

### Metrics and outputs you can truthfully show

Do not quote numbers you have not measured. Measure these on your own laptop and write them on a slide:

| Metric | How to get it |
|---|---|
| Tables / views / categories | 5 / 2 / 18 (`SHOW FULL TABLES;`, count query) |
| API endpoints | 13 (table in section 3) |
| Automated tests | 9 test methods in `FeedbackFlowTest`; `mvn test` prints pass/fail counts |
| Query plan improvement | `EXPLAIN ANALYZE` before/after on 100,000 rows (`indexes.sql`): note rows examined, `filesort`, and time |
| Reliability | Tests cover: duplicate ID, admin self-registration, wrong password, role block, rating 9, backward status, cross-user access, anonymous masking, DB-level CHECK/FK |

### Talking points and technical highlights

1. **Database-first design.** ER → relational schema → constraints → 3NF, then code. The app is thin because the database enforces the rules.
2. **Four integrity types, each demonstrable** (entity, referential, domain, unique/not-null).
3. **ACID in practice.** Three named transactions; `FOR UPDATE` row lock on status change; rollback demo.
4. **Security in layers.** BCrypt; session checked before controllers; role check on `/api/admin/**`; anonymous masking in the database view; least-privilege DB user; append-only audit tables.
5. **Honest trade-offs.** CSRF off with SameSite cookie; forward-only statuses (cannot reopen); no email/SMS/charts by design; `ENUM` is simple but changing statuses needs `ALTER TABLE` (a lookup table would be more flexible).

Likely questions and short answers:

| Question | Answer |
|---|---|
| Why not MongoDB? | Data is relational (users, categories, feedback, replies linked by FKs) and needs ACID transactions and constraints (slide 8). |
| Is it really 3NF? | No transitive dependencies: category name and user details are stored once and referenced by id. |
| What stops a user from seeing others' feedback? | Ownership check in `FeedbackService.myDetail`; other ids return 404. |
| Can management find an anonymous user? | Not through the app or `v_admin_feedback`. A DBA with direct table access still could, because `feedback.user_id` is stored. That is the trade-off of anonymous-but-accountable posting. |
| What if two admins change status together? | `SELECT … FOR UPDATE` serialises them; the second sees the new status and may get a 409. |

---

## 7. DEPLOYMENT INSTRUCTIONS

### Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| MySQL Server + Workbench | 8.0+ (CHECK constraints are enforced from 8.0.16) | `mysql --version` |

### Installation steps (Windows, PowerShell)

1. **Create the database.** Workbench → File → Open SQL Script → `database/schema.sql` → run all (lightning icon). Expect 5 tables, 2 views, 18 categories.
2. **Create the limited user.** Open `database/db_user.sql`, change the password, run it as root.
3. **Set the environment** (same PowerShell window you will run the app in):
   ```powershell
   $env:DB_PASSWORD = "the-password-you-set-in-db_user.sql"
   $env:ADMIN_PASSWORD = "choose-a-strong-one"      # skip for demo profile, which uses Admin@123
   ```
4. **Run.**
   ```powershell
   cd backend
   mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
   ```
   Open <http://localhost:8080>.

To use `root` temporarily while setting up: `$env:DB_USER="root"; $env:DB_PASSWORD="<root password>"`. Switch back to `feedback_app` before the demo.

### Sample data loading

| Method | What you get |
|---|---|
| `demo` profile (step 4) | Admin `ADMIN001` / `Admin@123`; users `STU2026001`, `STU2026002`, `STU2026003`, `FAC001`, `STF001`, `GRD001`, `HLP001`, all with password `Demo@1234`; 24 feedback, several moved to Under review / Action taken / Closed with replies. Runs only when the database has no users besides the admin. |
| `database/perf_seed.sql` | +100,000 synthetic feedback rows for the EXPLAIN comparison. Run only after at least one user exists. Clean-up statements are at the bottom of the file. |

To reset the demo: `DROP DATABASE college_feedback;`, then repeat steps 1 to 2.

### Running tests

```powershell
cd backend
mvn test
```

No MySQL is needed: tests use in-memory H2 in MySQL mode and execute the real `database/schema.sql`.

### Production-style run (not demo)

```powershell
$env:ADMIN_PASSWORD = "strong-password"
mvn -q package
java -jar target/college-feedback-1.0.0.jar
```

### Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Access denied for user 'feedback_app'` | Password in `DB_PASSWORD` differs from `db_user.sql`, or step 2 not run |
| `Unknown database 'college_feedback'` | `schema.sql` not run |
| Admin login fails | `ADMIN_PASSWORD` missing and not using the `demo` profile (see startup log warning); or an admin already exists with a different password |
| `Check constraint ... is violated` when seeding | MySQL older than 8.0.16 |
| Port 8080 busy | `$env:PORT = "8081"` |
