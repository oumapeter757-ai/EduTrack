package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.user.dto.CreateSuperAdminRequestDto;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.repository.UserRepository;
import com.peterscode.edutrack.user.service.UserRegistrationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for the initial SUPER_ADMIN signup.
 * This test is expected to FAIL on the current code if the bootstrap_lock
 * is not held through the entire create operation.
 */
@Tag("concurrency")
class SignupConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository testUserRepository;

    /**
     * Test: 10 threads attempt signup with valid token simultaneously.
     * Expected: Exactly 1 succeeds, 9 fail.
     */
    @Test
    void concurrentSignup_shouldCreateExactlyOneSuperAdmin() throws Exception {
        int threads = 10;
        String token = "test-bootstrap-token";

        TestResult<Object> result = runConcurrent(threads, () -> {
            CreateSuperAdminRequestDto dto = new CreateSuperAdminRequestDto();
            dto.setEmail("signup-" + System.currentTimeMillis() + "@" + schoolId + ".test.edu");
            dto.setPassword("Password123!");
            dto.setFirstName("Signup");
            dto.setLastName("User");
            registrationService.signupInitialSuperAdmin(dto, token);
            return "signedup";
        }, 30);

        // Count SUPER_ADMIN users
        List<com.peterscode.edutrack.user.entity.User> superAdmins = testUserRepository
                .findByRole(Role.SUPER_ADMIN);

        assertThat(superAdmins.size())
                .as("Exactly one SUPER_ADMIN should exist after concurrent signup")
                .isEqualTo(1);

        assertThat(result.successCount)
                .as("Exactly one thread should succeed")
                .isEqualTo(1);
    }
}
