package edu.sau.feedback.security;

import edu.sau.feedback.entity.Role;
import edu.sau.feedback.entity.User;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** Logged-in principal kept in the HTTP session. The hash is wiped right after authentication. */
public class AppUserDetails implements UserDetails, CredentialsContainer, Serializable {
    private final Long userId;
    private final String institutionalId;
    private final String fullName;
    private final Role role;
    private String passwordHash;

    public AppUserDetails(User u) {
        this.userId = u.getUserId();
        this.institutionalId = u.getInstitutionalId();
        this.fullName = u.getFullName();
        this.role = u.getRole();
        this.passwordHash = u.getPasswordHash();
    }

    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return role == Role.ADMIN; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return institutionalId; }
    @Override public void eraseCredentials() { this.passwordHash = null; }
}
