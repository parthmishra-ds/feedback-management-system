package edu.sau.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Alerts: in-app rows, contact details and privacy. Email and SMS are off in tests (they only log).
 * Uses its own in-memory database so the counts in FeedbackFlowTest are not disturbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(H2SchemaConfig.class)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:fbnotify;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    static MockHttpSession student, other, admin;
    static long feedbackId;

    private ResultActions postJson(MockHttpSession s, String url, String body) throws Exception {
        var req = post(url).contentType("application/json").content(body);
        if (s != null) req.session(s);
        return mvc.perform(req);
    }

    private MockHttpSession login(String id, String pw) throws Exception {
        var res = postJson(null, "/api/auth/login", "{\"institutionalId\":\"" + id + "\",\"password\":\"" + pw + "\"}")
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    @Test @Order(1)
    void contactDetailsAreValidatedAndStoredOnlyForTheCaller() throws Exception {
        postJson(null, "/api/auth/register", "{\"institutionalId\":\"N1\",\"fullName\":\"Notify Student\",\"password\":\"Passw0rd!\",\"role\":\"STUDENT\"}").andExpect(status().isCreated());
        postJson(null, "/api/auth/register", "{\"institutionalId\":\"N2\",\"fullName\":\"Other Student\",\"password\":\"Passw0rd!\",\"role\":\"STUDENT\"}").andExpect(status().isCreated());
        student = login("N1", "Passw0rd!");
        other = login("N2", "Passw0rd!");
        admin = login("ADMIN001", "Admin@123");

        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/contact").session(student).contentType("application/json").content("{\"email\":\"not-an-email\",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/contact").session(student).contentType("application/json").content("{\"email\":\"\",\"phone\":\"98765\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/contact").session(student).contentType("application/json").content("{\"email\":\"Notify@Example.com\",\"phone\":\"+919876543210\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("notify@example.com"));

        mvc.perform(get("/api/me/contact").session(student)).andExpect(jsonPath("$.phone").value("+919876543210"));
        mvc.perform(get("/api/me/contact").session(other)).andExpect(jsonPath("$.email").doesNotExist());
        // the update touched only the contact columns
        assertEquals(1, jdbc.queryForObject("select count(*) from users where institutional_id='N1' and password_hash like '$2%'", Integer.class));
    }

    @Test @Order(2)
    void newFeedbackAlertsManagementWithoutRevealingTheSender() throws Exception {
        var res = postJson(student, "/api/feedback", "{\"categoryId\":3,\"rating\":1,\"comments\":\"WiFi is down\",\"anonymous\":true}")
                .andExpect(status().isCreated()).andReturn();
        feedbackId = json.readTree(res.getResponse().getContentAsString()).get("feedbackId").asLong();

        String body = mvc.perform(get("/api/notifications").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.items[0].feedbackId").value(feedbackId))
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("Low rating: WiFi, 1 of 5"));
        assertFalse(body.contains("Notify Student"), "alert to management must not name the sender");

        mvc.perform(get("/api/notifications").session(student)).andExpect(jsonPath("$.unread").value(0));
    }

    @Test @Order(3)
    void statusChangeAndReplyAlertTheSubmitter() throws Exception {
        mvc.perform(patch("/api/admin/feedback/" + feedbackId + "/status").session(admin).contentType("application/json")
                .content("{\"newStatus\":\"UNDER_REVIEW\",\"note\":\"IT is checking\"}")).andExpect(status().isOk());
        postJson(admin, "/api/admin/feedback/" + feedbackId + "/responses", "{\"responseText\":\"Router replaced.\"}").andExpect(status().isCreated());

        mvc.perform(get("/api/notifications").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Management replied to your WiFi feedback"))
                .andExpect(jsonPath("$.items[1].title").value("Your WiFi feedback is now Under review"))
                .andExpect(jsonPath("$.items[1].message").value("Note from management: IT is checking"));

        // a failed action leaves no alert behind (same transaction)
        mvc.perform(patch("/api/admin/feedback/" + feedbackId + "/status").session(admin).contentType("application/json")
                .content("{\"newStatus\":\"SUBMITTED\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/notifications").session(student)).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test @Order(4)
    void readingIsPerUser() throws Exception {
        long first = json.readTree(mvc.perform(get("/api/notifications").session(student)).andReturn().getResponse().getContentAsString())
                .get("items").get(0).get("notificationId").asLong();

        // another user cannot mark my alert as read
        mvc.perform(post("/api/notifications/" + first + "/read").session(other)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").session(student)).andExpect(jsonPath("$.unread").value(2));

        mvc.perform(post("/api/notifications/" + first + "/read").session(student)).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post("/api/notifications/read-all").session(student)).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/notifications").session(admin)).andExpect(jsonPath("$.unread").value(1));
    }
}
