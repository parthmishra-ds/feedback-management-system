package edu.sau.feedback.config;

import edu.sau.feedback.entity.Role;
import edu.sau.feedback.repository.UserRepository;
import edu.sau.feedback.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the management (ADMIN) account at startup if none exists.
 * Credentials come from environment variables, never from source code:
 *   ADMIN_ID (default ADMIN001), ADMIN_NAME, ADMIN_PASSWORD (required, min 8 chars).
 */
@Component
@Order(1)
public class AdminBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final AuthService auth;
    @Value("${app.admin.institutional-id}") private String adminId;
    @Value("${app.admin.name}") private String adminName;
    @Value("${app.admin.password:}") private String adminPassword;

    public AdminBootstrap(UserRepository users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @Override
    public void run(String... args) {
        if (users.existsByRole(Role.ADMIN)) return;
        if (adminPassword == null || adminPassword.length() < 8) {
            log.warn("No admin account exists and ADMIN_PASSWORD is not set (min 8 chars). Management login will not work.");
            return;
        }
        auth.createUser(adminId, adminName, adminPassword, Role.ADMIN, "Management");
        log.info("Created admin account '{}'", adminId);
    }
}
