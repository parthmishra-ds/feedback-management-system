package edu.sau.feedback.service;

import edu.sau.feedback.dto.Dtos.RegisterRequest;
import edu.sau.feedback.dto.Dtos.UserInfo;
import edu.sau.feedback.entity.Role;
import edu.sau.feedback.entity.User;
import edu.sau.feedback.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Self-registration. ADMIN accounts can never be created here. */
    @Transactional
    public UserInfo register(RegisterRequest req) {
        if (req.role() == Role.ADMIN) throw ApiException.forbidden("Admin accounts cannot be self-registered");
        String id = req.institutionalId().trim();
        if (users.existsByInstitutionalId(id)) throw ApiException.conflict("An account with this college ID already exists");
        try {
            return toInfo(createUser(id, req.fullName().trim(), req.password(), req.role(), req.department()));
        } catch (DataIntegrityViolationException e) {      // two people registering the same ID at once
            throw ApiException.conflict("An account with this college ID already exists");
        }
    }

    /** Used by register, the admin bootstrap and the demo seeder. Stores a BCrypt hash only. */
    @Transactional
    public User createUser(String institutionalId, String fullName, String rawPassword, Role role, String department) {
        User u = new User();
        u.setInstitutionalId(institutionalId);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(role);
        u.setDepartment(department == null || department.isBlank() ? null : department.trim());
        return users.saveAndFlush(u);
    }

    public static UserInfo toInfo(User u) {
        return new UserInfo(u.getUserId(), u.getInstitutionalId(), u.getFullName(), u.getRole(), u.getDepartment());
    }
}
