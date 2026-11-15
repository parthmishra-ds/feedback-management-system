# College Feedback Management System

Database Systems project, MCA, South Asian University. Spring Boot 3 + MySQL 8 + plain HTML/CSS/JS.
Full specification: [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md).

## Quick start (Windows PowerShell)

1. Workbench: run `database/schema.sql`, then `database/db_user.sql` (change its password first).
2. In PowerShell:
   ```powershell
   $env:DB_PASSWORD = "<password from db_user.sql>"
   cd backend
   mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
   ```
3. Open http://localhost:8080

| Login | Password |
|---|---|
| `ADMIN001` (management) | `Admin@123` (demo profile only) |
| `STU2026001` (student) | `Demo@1234` |

## Alerts: in-app, email and SMS

* **In-app:** the **Alerts** button in the top bar shows a count and a list. Management is alerted to new feedback; the sender is alerted when the status changes or management replies. Alerts to management never name the sender, so anonymous feedback stays anonymous.
* **Email and SMS:** each person adds an email and/or mobile number under **Alerts**. Email goes for every alert; SMS only for replies, *Action taken*, *Closed*, and low ratings (2 or less) sent to management.
* **Both are OFF by default.** With the defaults the message is only written to the server log (`[EMAIL not sent ...]`), so a demo never sends anything by accident. To send for real, set environment variables before starting:

  ```bash
  # Email through any SMTP server (Gmail needs an App Password, not your normal password)
  export NOTIFY_EMAIL=true MAIL_HOST=smtp.gmail.com MAIL_USER=you@gmail.com MAIL_PASSWORD='app-password' MAIL_FROM=you@gmail.com
  # SMS through Twilio (needs a Twilio account and a Twilio number)
  export NOTIFY_SMS=true TWILIO_SID=ACxxxxxxxx TWILIO_TOKEN=xxxxxxxx TWILIO_FROM=+1xxxxxxxxxx
  ```
* **Pop-up alerts:** the Alerts panel has an "Allow pop-up alerts" button. While the page is open (also on a phone browser that allows it) a new alert shows as a system notification. Alerts when the page is closed would need a service worker and a push service; that is not built.
* **Existing database:** run `database/migration_notifications.sql` once as root (adds the `email`/`phone` columns, the `notifications` table and the grants). A fresh database made from the new `schema.sql` does not need it.

## Verification status (read before the demo)

| Item | Status |
|---|---|
| `database/schema.sql` on MySQL 8 | **Not run.** Written for MySQL 8.0.16+. Run it once and fix any error before presenting. |
| Java backend compile and `mvn test` | 9 of 9 tests passed on the team laptop **before** the alerts feature. The alerts code and `NotificationFlowTest` (4 tests) were written without a compiler available: run `mvn test` and fix anything it reports. |
| `database/indexes.sql`, `perf_seed.sql` | **Not run.** No performance numbers are claimed anywhere. Record your own. |
| Frontend | Walked through in a browser against a mock API (login, give feedback, status bar, admin dashboard, reply, status change, phone width) and scanned with axe-core: no violations. Not yet re-checked against the real Spring API after the latest UI change. |

If `mvn test` fails, fix that before touching the demo. Most likely suspects: H2 handling of `ENUM` or the views in `schema.sql`, or a small compile error.

## Theme

Navy and gold palette (matching the SAU Mess site) defined once as CSS variables at the top of `backend/src/main/resources/static/css/theme.css`.
