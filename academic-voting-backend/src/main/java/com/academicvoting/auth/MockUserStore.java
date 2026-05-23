package com.academicvoting.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory university user registry.
 *
 * <p>Loads mock users from {@code application.yml} (under {@code mock.users}),
 * BCrypt-hashes their passwords at startup, and exposes lookup operations.
 *
 * <p><strong>Production replacement</strong>: swap this with a JPA repository
 * or an LDAP/Active Directory lookup against the real university directory.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockUserStore {

    private final PasswordEncoder         passwordEncoder;
    private final MockUsersProperties     properties;

    /** userId → UniversityUser (with hashed password). */
    private final Map<String, UniversityUser> store = new ConcurrentHashMap<>();

    /**
     * Initialises the user store on startup:
     * reads plain-text passwords from config, hashes them, then discards the originals.
     */
    @PostConstruct
    public void init() {
        for (MockUsersProperties.MockUserEntry entry : properties.getUsers()) {
            UniversityUser user = UniversityUser.builder()
                .userId(entry.getUserId())
                .name(entry.getName())
                .email(entry.getEmail())
                .role(entry.getRole())
                .passwordHash(passwordEncoder.encode(entry.getPassword()))
                .build();
            store.put(entry.getUserId(), user);
            log.debug("Registered mock user: {} [{}]", entry.getUserId(), entry.getRole());
        }
        log.info("MockUserStore initialised — {} users loaded", store.size());
    }

    /**
     * Look up a user by their institutional ID.
     *
     * @param userId Institutional user ID (e.g., "student001")
     * @return Optional containing the user, or empty if not found
     */
    public Optional<UniversityUser> findById(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    /**
     * Verify a plain-text password against the stored BCrypt hash.
     *
     * @param user          The university user
     * @param rawPassword   The plain-text password to verify
     * @return {@code true} if the password matches
     */
    public boolean verifyPassword(UniversityUser user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    // ── Inner config properties class ─────────────────────────────────────────

    /**
     * Binds the {@code mock.users} list from {@code application.yml}.
     */
    @Configuration
    @ConfigurationProperties(prefix = "mock")
    public static class MockUsersProperties {

        private List<MockUserEntry> users = List.of();

        public List<MockUserEntry> getUsers() { return users; }
        public void setUsers(List<MockUserEntry> users) { this.users = users; }

        public static class MockUserEntry {
            private String userId;
            private String name;
            private String email;
            private String role;
            private String password;   // plain-text — only used during @PostConstruct hashing

            public String getUserId()   { return userId; }
            public String getName()     { return name; }
            public String getEmail()    { return email; }
            public String getRole()     { return role; }
            public String getPassword() { return password; }

            public void setUserId(String v)   { this.userId   = v; }
            public void setName(String v)     { this.name     = v; }
            public void setEmail(String v)    { this.email    = v; }
            public void setRole(String v)     { this.role     = v; }
            public void setPassword(String v) { this.password = v; }
        }
    }
}
