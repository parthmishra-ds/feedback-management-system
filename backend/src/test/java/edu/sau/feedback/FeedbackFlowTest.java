package edu.sau.feedback;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end flow through HTTP -> security -> service -> (H2 running schema.sql). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(H2SchemaConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeedbackFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    static MockHttpSession student, admin;
    static long feedbackId, anonId;

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
    void schemaHas18CategoriesAndPasswordsAreHashed() throws Exception {
        assertEquals(18, jdbc.queryForObject("select count(*) from feedback_categories", Integer.class));
        postJson(null, "/api/auth/register",
                "{\"institutionalId\":\"S1\",\"fullName\":\"Test Student\",\"password\":\"Passw0rd!\",\"role\":\"STUDENT\",\"department\":\"CS\"}")
                .andExpect(status().isCreated());
        String hash = jdbc.queryForObject("select password_hash from users where institutional_id='S1'", String.class);
        assertTrue(hash.startsWith("$2"), "BCrypt hash expected");
        assertNotEquals("Passw0rd!", hash);
    }

    @Test @Order(2)
    void registrationRules() throws Exception {
        postJson(null, "/api/auth/register", "{\"institutionalId\":\"S1\",\"fullName\":\"Dup\",\"password\":\"Passw0rd!\",\"role\":\"STUDENT\"}")
                .andExpect(status().isConflict());
        postJson(null, "/api/auth/register", "{\"institutionalId\":\"X9\",\"fullName\":\"Evil\",\"password\":\"Passw0rd!\",\"role\":\"ADMIN\"}")
                .andExpect(status().isForbidden());
        postJson(null, "/api/auth/register", "{\"institutionalId\":\"X8\",\"fullName\":\"Short\",\"password\":\"123\",\"role\":\"STUDENT\"}")
                .andExpect(status().isBadRequest());
    }

    @Test @Order(3)
    void loginAndProtectedRoutes() throws Exception {
        mvc.perform(get("/api/feedback/mine")).andExpect(status().isUnauthorized());
        postJson(null, "/api/auth/login", "{\"institutionalId\":\"S1\",\"password\":\"wrong-pass\"}").andExpect(status().isUnauthorized());
        student = login("S1", "Passw0rd!");
        admin = login("ADMIN001", "Admin@123");
        mvc.perform(get("/api/admin/dashboard").session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(student)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test @Order(4)
    void submitCreatesFeedbackAndFirstHistoryRow() throws Exception {
        var res = postJson(student, "/api/feedback", "{\"categoryId\":3,\"rating\":2,\"comments\":\"WiFi is slow\",\"anonymous\":false}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED")).andReturn();
        feedbackId = json.readTree(res.getResponse().getContentAsString()).get("feedbackId").asLong();
        assertEquals(1, jdbc.queryForObject("select count(*) from feedback_status_history where feedback_id=? and old_status is null and new_status='SUBMITTED'", Integer.class, feedbackId));

        postJson(student, "/api/feedback", "{\"categoryId\":3,\"rating\":9,\"comments\":\"x\"}").andExpect(status().isBadRequest());
        postJson(student, "/api/feedback", "{\"categoryId\":999,\"rating\":3}").andExpect(status().isBadRequest());
    }

    @Test @Order(5)
    void anonymousPostIsHiddenFromManagement() throws Exception {
        var res = postJson(student, "/api/feedback", "{\"categoryId\":2,\"rating\":1,\"comments\":\"Insect in food\",\"anonymous\":true}")
                .andExpect(status().isCreated()).andReturn();
        anonId = json.readTree(res.getResponse().getContentAsString()).get("feedbackId").asLong();

        String body = mvc.perform(get("/api/admin/feedback/" + anonId).session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Test Student"), "admin response must not contain the submitter's name");
        JsonNode n = json.readTree(body);
        assertEquals("Anonymous", n.get("submitterName").asText());
        assertEquals("System", n.get("history").get(0).get("byWhom").asText());

        // the submitter can still see their own anonymous post
        mvc.perform(get("/api/feedback/" + anonId).session(student)).andExpect(status().isOk());
        // the named one shows the name to management
        mvc.perform(get("/api/admin/feedback/" + feedbackId).session(admin)).andExpect(jsonPath("$.submitterName").value("Test Student"));
    }

    @Test @Order(6)
    void statusLifecycleIsForwardOnlyAndAudited() throws Exception {
        String url = "/api/admin/feedback/" + feedbackId + "/status";
        mvc.perform(patch(url).session(student).contentType("application/json").content("{\"newStatus\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(url).session(admin).contentType("application/json").content("{\"newStatus\":\"UNDER_REVIEW\",\"note\":\"Looking into it\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        mvc.perform(patch(url).session(admin).contentType("application/json").content("{\"newStatus\":\"SUBMITTED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(url).session(admin).contentType("application/json").content("{\"newStatus\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(url).session(admin).contentType("application/json").content("{\"newStatus\":\"ACTION_TAKEN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(3));
        assertEquals(3, jdbc.queryForObject("select count(*) from feedback_status_history where feedback_id=?", Integer.class, feedbackId));
        assertEquals("Management Admin", jdbc.queryForObject(
                "select u.full_name from feedback_status_history h join users u on u.user_id=h.changed_by where h.feedback_id=? and h.new_status='UNDER_REVIEW'",
                String.class, feedbackId));
    }

    @Test @Order(7)
    void responsesAreStoredAndVisibleToOwnerOnly() throws Exception {
        postJson(admin, "/api/admin/feedback/" + feedbackId + "/responses", "{\"responseText\":\"We replaced the router.\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.responses[0].responseText").value("We replaced the router."));
        postJson(admin, "/api/admin/feedback/" + feedbackId + "/responses", "{\"responseText\":\"   \"}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/feedback/" + feedbackId).session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.responses.length()").value(1));

        postJson(null, "/api/auth/register", "{\"institutionalId\":\"S2\",\"fullName\":\"Other\",\"password\":\"Passw0rd!\",\"role\":\"STUDENT\"}");
        MockHttpSession other = login("S2", "Passw0rd!");
        mvc.perform(get("/api/feedback/" + feedbackId).session(other)).andExpect(status().isNotFound());
    }

    @Test @Order(8)
    void dashboardAndDatabaseConstraints() throws Exception {
        mvc.perform(get("/api/admin/dashboard").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFeedback").value(2))
                .andExpect(jsonPath("$.averageRating").value(1.5))
                .andExpect(jsonPath("$.categories.length()").value(18))
                .andExpect(jsonPath("$.categories[0].categoryName").value("Mess Food"));   // worst-rated first
        mvc.perform(get("/api/admin/feedback?pending=true").session(admin)).andExpect(jsonPath("$.total").value(1));

        // the database itself rejects bad data even if the application had a bug
        assertThrows(Exception.class, () -> jdbc.update("insert into feedback (user_id, category_id, rating) values (1, 1, 9)"));
        assertThrows(Exception.class, () -> jdbc.update("insert into feedback (user_id, category_id, rating) values (99999, 1, 3)"));
    }

    @Test @Order(9)
    void logoutEndsSession() throws Exception {
        mvc.perform(post("/api/auth/logout").session(student)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(student)).andExpect(status().isUnauthorized());
    }
}
