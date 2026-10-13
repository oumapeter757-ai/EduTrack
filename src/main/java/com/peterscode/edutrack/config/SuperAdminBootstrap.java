package com.peterscode.edutrack.config;

import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the initial SUPER_ADMIN account on startup when bootstrap profile is enabled.
 * <p>
 * Credentials can be provided via environment variables and changed immediately
 * after first login.
 * <p>
 * Active only when 'bootstrap' profile is enabled:
 * {@code SPRING_PROFILES_ACTIVE=bootstrap}
 */
@Slf4j
@Component
@Profile("bootstrap")
@RequiredArgsConstructor
public class SuperAdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${edutrack.super-admin.email:admin@edutrack.com}")
    private String email;

    @Value("${edutrack.super-admin.username:superadmin}")
    private String username;

    @Value("${edutrack.super-admin.password:#{null}}")
    private String password;

    @Value("${edutrack.super-admin.first-name:Super}")
    private String firstName;

    @Value("${edutrack.super-admin.last-name:Admin}")
    private String lastName;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            log.info("SUPER_ADMIN already exists, skipping bootstrap");
            return;
        }

        if (password == null || password.isBlank()) {
            log.error("Cannot bootstrap SUPER_ADMIN: password not provided. " +
                    "Set EDUTRACK_SUPER_ADMIN_PASSWORD environment variable.");
            return;
        }

        User superAdmin = User.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .schoolId(null) // SUPER_ADMIN is platform-wide, not school-specific
                .role(Role.SUPER_ADMIN)
                .active(true)
                .build();

        userRepository.save(superAdmin);
        log.info("SUPER_ADMIN bootstrapped successfully with email: {}", email);
        log.warn("IMPORTANT: Change the SUPER_ADMIN password immediately after first login!");
    }
}
