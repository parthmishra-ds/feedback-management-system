package edu.sau.feedback.config;

import edu.sau.feedback.dto.Dtos.*;
import edu.sau.feedback.entity.*;
import edu.sau.feedback.repository.CategoryRepository;
import edu.sau.feedback.repository.UserRepository;
import edu.sau.feedback.service.AuthService;
import edu.sau.feedback.service.FeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Sample data for the live demo. Active only with  --spring.profiles.active=demo.
 * Everything goes through the real services, so history rows and replies are created exactly as in production.
 * Logins: every user has password  Demo@1234  (admin: see ADMIN_PASSWORD).
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private record Seed(String uid, String cat, int rating, String comment, boolean anon) {}

    private final UserRepository users;
    private final CategoryRepository categories;
    private final AuthService auth;
    private final FeedbackService feedback;

    public DemoDataSeeder(UserRepository users, CategoryRepository categories, AuthService auth, FeedbackService feedback) {
        this.users = users;
        this.categories = categories;
        this.auth = auth;
        this.feedback = feedback;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 1) return;      // already seeded (only the admin exists on a fresh database)
        User admin = users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).findFirst().orElse(null);
        if (admin == null) { log.warn("Demo seeding skipped: no admin account"); return; }

        String pw = "Demo@1234";
        Map<String, Long> ids = new HashMap<>();
        Object[][] people = {
            {"STU2026001", "Aarav Sharma",   Role.STUDENT, "Computer Applications"},
            {"STU2026002", "Priya Nair",     Role.STUDENT, "Economics"},
            {"STU2026003", "Rohan Mehta",    Role.STUDENT, "Mathematics"},
            {"FAC001",     "Dr. Meera Kapoor", Role.FACULTY, "Computer Applications"},
            {"STF001",     "Suresh Yadav",   Role.STAFF,   "Administration"},
            {"GRD001",     "Ramesh Singh",   Role.GUARD,   "Security"},
            {"HLP001",     "Sunita Devi",    Role.HELPER,  "Housekeeping"},
        };
        for (Object[] p : people) {
            ids.put((String) p[0], auth.createUser((String) p[0], (String) p[1], pw, (Role) p[2], (String) p[3]).getUserId());
        }
        Map<String, Long> cat = new HashMap<>();
        categories.findAll().forEach(c -> cat.put(c.getCategoryName(), c.getCategoryId()));

        Seed[] seeds = {
            new Seed("STU2026001", "WiFi", 1, "WiFi drops every evening in the hostel block. Cannot attend online sessions.", false),
            new Seed("STU2026002", "WiFi", 2, "Very slow speed near the library.", true),
            new Seed("STU2026003", "Water", 2, "Cooler on the 2nd floor has been out of order for two weeks.", false),
            new Seed("STU2026001", "Mess Food", 2, "Dal is watery and rice is often undercooked.", false),
            new Seed("STU2026002", "Mess Food", 1, "Found an insect in the sabzi on Tuesday.", true),
            new Seed("STU2026003", "Mess Food", 3, "Better this week, but the menu repeats a lot.", false),
            new Seed("FAC001",     "Library", 4, "Good collection. Please extend weekend closing time.", false),
            new Seed("STU2026001", "Library", 5, "Quiet and well maintained.", false),
            new Seed("STU2026002", "Lift", 1, "Lift in the academic block stuck between floors.", true),
            new Seed("STF001",     "Lift", 2, "Lift needs maintenance, makes a loud noise.", false),
            new Seed("GRD001",     "Electricity", 2, "Gate cabin has no working light at night.", false),
            new Seed("HLP001",     "Washrooms", 2, "Taps broken in the ground floor washroom.", false),
            new Seed("STU2026003", "Classrooms", 3, "Projector in Room 204 is dim.", false),
            new Seed("FAC001",     "Laboratories", 4, "Labs are fine, need two more systems.", false),
            new Seed("STU2026001", "Transport", 3, "Bus arrives 15 minutes late most mornings.", false),
            new Seed("STU2026002", "Sports and Gym", 5, "Great gym equipment.", false),
            new Seed("STF001",     "Administration", 3, "Bonafide certificates take too long.", false),
            new Seed("STU2026003", "Security", 4, "Feel safe on campus.", false),
            new Seed("STU2026001", "Canteen", 4, "Reasonable prices.", false),
            new Seed("HLP001",     "Cleanliness", 3, "Dustbins near the canteen overflow by noon.", true),
            new Seed("STU2026002", "Medical Centre", 4, "Doctor was helpful.", false),
            new Seed("STU2026003", "Hostel Rooms", 2, "Fan regulator is broken, reported twice to warden.", false),
            new Seed("FAC001",     "Teaching Quality", 5, "Excellent peer-learning culture.", false),
            new Seed("GRD001",     "Water", 3, "Drinking water is sometimes muddy in the morning.", false),
        };
        Map<Integer, Long> created = new HashMap<>();
        for (int i = 0; i < seeds.length; i++) {
            Seed s = seeds[i];
            var d = feedback.submit(ids.get(s.uid()),
                    new FeedbackRequest(cat.get(s.cat()), s.rating(), s.comment(), s.anon()));
            created.put(i, d.feedbackId());
        }

        // move a few through the lifecycle, with replies, so the dashboard and audit trail have something to show
        Long aid = admin.getUserId();
        advance(aid, created.get(0), Status.UNDER_REVIEW, "Forwarded to IT team", "Thank you. IT has been asked to check the hostel access points.");
        advance(aid, created.get(0), Status.ACTION_TAKEN, "Two access points replaced", null);
        advance(aid, created.get(2), Status.UNDER_REVIEW, "Maintenance notified", null);
        advance(aid, created.get(4), Status.UNDER_REVIEW, "Mess committee informed", "This is unacceptable and has been escalated to the mess contractor.");
        advance(aid, created.get(4), Status.ACTION_TAKEN, "Contractor warned in writing", null);
        advance(aid, created.get(4), Status.CLOSED, "Verified by warden", "Inspection done. Closing after confirming corrective steps.");
        advance(aid, created.get(8), Status.UNDER_REVIEW, "Lift vendor called", null);
        advance(aid, created.get(7), Status.CLOSED, "Appreciation noted", "Thank you for the kind words.");
        log.info("Demo data loaded: {} users, {} feedback", people.length, seeds.length);
    }

    private void advance(Long adminId, Long feedbackId, Status to, String note, String reply) {
        feedback.changeStatus(adminId, feedbackId, new StatusChangeRequest(to, note));
        if (reply != null) feedback.addResponse(adminId, feedbackId, new ResponseRequest(reply));
    }
}
