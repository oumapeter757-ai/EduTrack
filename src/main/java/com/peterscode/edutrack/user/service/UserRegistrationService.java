package com.peterscode.edutrack.user.service;

import com.peterscode.edutrack.user.dto.CreateSchoolAdminRequestDto;
import com.peterscode.edutrack.user.dto.CreateSchoolUserRequestDto;
import com.peterscode.edutrack.user.dto.CreateSuperAdminRequestDto;
import com.peterscode.edutrack.user.dto.CreateUserRequestDto;
import com.peterscode.edutrack.user.dto.UserResponseDto;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.security.PlanGuard;
import com.peterscode.edutrack.security.AuthRateLimiter;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.exception.SchoolInactiveException;
import com.peterscode.edutrack.shared.exception.BootstrapNotConfiguredException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.beans.factory.annotation.Value;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Service for user registration with hierarchical role enforcement.
 * <p>
 * Registration rules:
 * <ul>
 *   <li>Initial SUPER_ADMIN can self-register when no SUPER_ADMIN exists in the system (signup is disabled thereafter)</li>
 *   <li>SUPER_ADMIN creates SUPER_ADMIN and SCHOOL_ADMIN for any school</li>
 *   <li>SCHOOL_ADMIN creates TEACHER, STUDENT, PARENT for their own school only</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SchoolRepository schoolRepository;
    private final PlanGuard planGuard;
    private final EntityManager entityManager;
    private final AuthRateLimiter rateLimiter;
    @Value("${edutrack.bootstrap.token:}")
    private String bootstrapToken;

    /**
     * Initial open signup for the first SUPER_ADMIN.
     * Allowed ONLY when no SUPER_ADMIN exists in the system.
     * Once a SUPER_ADMIN exists, signup is disabled.
     */
    @Transactional
    public UserResponseDto signupInitialSuperAdmin(CreateSuperAdminRequestDto request, String suppliedToken) {
        String email = request.getEmail();
        rateLimiter.checkSignup(email);
        if (bootstrapToken == null || bootstrapToken.isBlank()
                || suppliedToken == null
                || !MessageDigest.isEqual(bootstrapToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            rateLimiter.failure(email);
            throw new UnauthorizedOperationException("Registration is disabled");
        }
        if (entityManager.createNativeQuery("SELECT id FROM edutrack_bootstrap_lock WHERE id = 1 FOR UPDATE")
                .getResultList().isEmpty()) {
            throw new BootstrapNotConfiguredException();
        }
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            rateLimiter.failure(email);
            throw new UnauthorizedOperationException("Registration is disabled. A Super Admin already exists.");
        }
        UserResponseDto response = createUser(request, null, Role.SUPER_ADMIN);
        rateLimiter.success(email);
        return response;
    }

    /**
     * SUPER_ADMIN creates another SUPER_ADMIN.
     */
    @Transactional
    public UserResponseDto createSuperAdmin(CreateSuperAdminRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SUPER_ADMIN, Role.SUPER_ADMIN);
        return createUser(request, null, Role.SUPER_ADMIN);
    }

    /**
     * SUPER_ADMIN creates a SCHOOL_ADMIN for a specified school.
     */
    @Transactional
    public UserResponseDto createSchoolAdmin(CreateSchoolAdminRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SUPER_ADMIN, Role.SCHOOL_ADMIN);
        requireActiveSchool(request.getSchoolId());
        return createUser(request, request.getSchoolId(), Role.SCHOOL_ADMIN);
    }

    /**
     * SCHOOL_ADMIN creates a TEACHER for their own school.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponseDto createTeacher(CreateSchoolUserRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SCHOOL_ADMIN, Role.TEACHER);
        Long schoolId = requireSchool(creator);
        planGuard.assertTeacherCapacity(schoolId, 1);
        return createUser(request, schoolId, Role.TEACHER);
    }

    /**
     * SCHOOL_ADMIN creates a STUDENT for their own school.
     */
    @Transactional
    public UserResponseDto createStudent(CreateSchoolUserRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SCHOOL_ADMIN, Role.STUDENT);
        return createUser(request, requireSchool(creator), Role.STUDENT);
    }

    /**
     * SCHOOL_ADMIN creates a PARENT for their own school.
     */
    @Transactional
    public UserResponseDto createParent(CreateSchoolUserRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SCHOOL_ADMIN, Role.PARENT);
        return createUser(request, requireSchool(creator), Role.PARENT);
    }

    private void validateCreatorRole(UserPrincipal creator, Role requiredRole, Role targetRole) {
        if (creator == null) {
            throw new UnauthorizedOperationException("Authenticated user is required to perform this operation");
        }
        Role creatorRole = creator.getRole();
        
        if (creatorRole != requiredRole) {
            throw new UnauthorizedOperationException(
                    "Only %s can create %s accounts".formatted(requiredRole, targetRole)
            );
        }
        
        if (!Role.canCreate(creatorRole, targetRole)) {
            throw new UnauthorizedOperationException(
                    "%s is not authorized to create %s accounts".formatted(creatorRole, targetRole)
            );
        }
    }

    private UserResponseDto createUser(CreateUserRequestDto request, Long schoolId, Role role) {
        String email = request.getEmail().trim().toLowerCase();
        String username = request.getUsername().trim();
        validateUniqueConstraints(email, username);

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .schoolId(schoolId)
                .role(role)
                .active(true)
                .build();

        user = userRepository.saveAndFlush(user);

        return UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .schoolId(user.getSchoolId())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }

    private void validateUniqueConstraints(String email, String username) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("User", "username", username);
        }
    }

    private Long requireSchool(UserPrincipal creator) {
        if (creator == null || creator.getSchoolId() == null) {
            throw new UnauthorizedOperationException("School context is required");
        }
        requireActiveSchool(creator.getSchoolId());
        return creator.getSchoolId();
    }

    private void requireActiveSchool(Long schoolId) {
        if (schoolId == null) throw new SchoolNotFoundException("School not found");
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));
        if (!school.isActive()) throw new SchoolInactiveException("School is inactive");
    }
}
