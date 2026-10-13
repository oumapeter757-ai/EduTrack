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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Initial open signup for the first SUPER_ADMIN.
     * Allowed ONLY when no SUPER_ADMIN exists in the system.
     * Once a SUPER_ADMIN exists, signup is disabled.
     */
    @Transactional
    public UserResponseDto signupInitialSuperAdmin(CreateSuperAdminRequestDto request) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            throw new UnauthorizedOperationException("Registration is disabled. A Super Admin already exists.");
        }
        return createUser(request, null, Role.SUPER_ADMIN);
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
        return createUser(request, request.getSchoolId(), Role.SCHOOL_ADMIN);
    }

    /**
     * SCHOOL_ADMIN creates a TEACHER for their own school.
     */
    @Transactional
    public UserResponseDto createTeacher(CreateSchoolUserRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SCHOOL_ADMIN, Role.TEACHER);
        return createUser(request, creator.getSchoolId(), Role.TEACHER);
    }

    /**
     * SCHOOL_ADMIN creates a STUDENT for their own school.
     */
    @Transactional
    public UserResponseDto createStudent(CreateSchoolUserRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SCHOOL_ADMIN, Role.STUDENT);
        return createUser(request, creator.getSchoolId(), Role.STUDENT);
    }

    /**
     * SCHOOL_ADMIN creates a PARENT for their own school.
     */
    @Transactional
    public UserResponseDto createParent(CreateSchoolUserRequestDto request, UserPrincipal creator) {
        validateCreatorRole(creator, Role.SCHOOL_ADMIN, Role.PARENT);
        return createUser(request, creator.getSchoolId(), Role.PARENT);
    }

    private void validateCreatorRole(UserPrincipal creator, Role requiredRole, Role targetRole) {
        if (creator == null) {
            throw new UnauthorizedOperationException("Authenticated user is required to perform this operation");
        }
        Role creatorRole = extractRole(creator);
        
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

    private Role extractRole(UserPrincipal principal) {
        String authority = principal.getAuthorities().iterator().next().getAuthority();
        return Role.valueOf(authority.replace("ROLE_", ""));
    }

    private UserResponseDto createUser(CreateUserRequestDto request, Long schoolId, Role role) {
        validateUniqueConstraints(request.getEmail(), request.getUsername());

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .schoolId(schoolId)
                .role(role)
                .active(true)
                .build();

        user = userRepository.save(user);

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
            throw new IllegalArgumentException("Email is already in use");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
    }
}
