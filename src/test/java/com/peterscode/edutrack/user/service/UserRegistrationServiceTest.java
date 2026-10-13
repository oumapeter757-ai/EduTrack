package com.peterscode.edutrack.user.service;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.user.dto.CreateSchoolAdminRequestDto;
import com.peterscode.edutrack.user.dto.CreateSuperAdminRequestDto;
import com.peterscode.edutrack.user.dto.UserResponseDto;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    private CreateSuperAdminRequestDto superAdminRequest;
    private UserPrincipal superAdminPrincipal;
    private UserPrincipal schoolAdminPrincipal;

    @BeforeEach
    void setUp() {
        superAdminRequest = new CreateSuperAdminRequestDto();
        superAdminRequest.setFirstName("Super");
        superAdminRequest.setLastName("Admin");
        superAdminRequest.setUsername("superadmin");
        superAdminRequest.setEmail("superadmin@example.com");
        superAdminRequest.setPassword("Secret123!");

        User superAdminUser = User.builder()
                .id(1L)
                .email("superadmin@example.com")
                .role(Role.SUPER_ADMIN)
                .schoolId(0L)
                .active(true)
                .build();
        superAdminPrincipal = UserPrincipal.create(superAdminUser);

        User schoolAdminUser = User.builder()
                .id(2L)
                .email("schooladmin@example.com")
                .role(Role.SCHOOL_ADMIN)
                .schoolId(10L)
                .active(true)
                .build();
        schoolAdminPrincipal = UserPrincipal.create(schoolAdminUser);
    }

    @Test
    @DisplayName("signupInitialSuperAdmin succeeds when no super admin exists in the system")
    void signupInitialSuperAdmin_WhenNoSuperAdminExists_ShouldSucceed() {
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(false);
        when(userRepository.existsByUsername("superadmin")).thenReturn(false);
        when(userRepository.existsByEmail("superadmin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .schoolId(user.getSchoolId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .password(user.getPassword())
                    .role(user.getRole())
                    .active(true)
                    .build();
        });

        UserResponseDto response = userRegistrationService.signupInitialSuperAdmin(superAdminRequest);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("superadmin");
        assertThat(response.getEmail()).isEqualTo("superadmin@example.com");
        assertThat(response.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(response.getSchoolId()).isEqualTo(0L);

        verify(userRepository).existsByRole(Role.SUPER_ADMIN);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("signupInitialSuperAdmin throws UnauthorizedOperationException when super admin already exists")
    void signupInitialSuperAdmin_WhenSuperAdminAlreadyExists_ShouldThrowUnauthorizedOperationException() {
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> userRegistrationService.signupInitialSuperAdmin(superAdminRequest))
                .isInstanceOf(UnauthorizedOperationException.class)
                .hasMessageContaining("Registration is disabled. A Super Admin already exists");

        verify(userRepository).existsByRole(Role.SUPER_ADMIN);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSuperAdmin by logged-in SUPER_ADMIN succeeds")
    void createSuperAdmin_WhenCreatorIsSuperAdmin_ShouldSucceed() {
        when(userRepository.existsByUsername("superadmin")).thenReturn(false);
        when(userRepository.existsByEmail("superadmin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .schoolId(user.getSchoolId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .password(user.getPassword())
                    .role(user.getRole())
                    .active(true)
                    .build();
        });

        UserResponseDto response = userRegistrationService.createSuperAdmin(superAdminRequest, superAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(response.getSchoolId()).isEqualTo(0L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createSuperAdmin by non-SUPER_ADMIN throws UnauthorizedOperationException")
    void createSuperAdmin_WhenCreatorIsNotSuperAdmin_ShouldThrowUnauthorizedOperationException() {
        assertThatThrownBy(() -> userRegistrationService.createSuperAdmin(superAdminRequest, schoolAdminPrincipal))
                .isInstanceOf(UnauthorizedOperationException.class);

        verify(userRepository, never()).save(any());
    }
}
