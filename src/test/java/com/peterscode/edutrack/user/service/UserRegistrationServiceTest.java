package com.peterscode.edutrack.user.service;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.user.dto.CreateSchoolAdminRequestDto;
import com.peterscode.edutrack.user.dto.CreateSchoolUserRequestDto;
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
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.AuthRateLimiter;
import com.peterscode.edutrack.security.PlanGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private PlanGuard planGuard;

    @Mock
    private ModuleAccessService moduleAccessService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AuthRateLimiter rateLimiter;

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

        School school = School.builder().name("Test School").code("TEST").subdomain("test").active(true).build();
        school.setId(10L);
        lenient().when(schoolRepository.findById(10L)).thenReturn(Optional.of(school));

        ReflectionTestUtils.setField(userRegistrationService, "bootstrapToken", "test-token");
    }

    @Test
    @DisplayName("signupInitialSuperAdmin succeeds when no super admin exists in the system")
    void signupInitialSuperAdmin_WhenNoSuperAdminExists_ShouldSucceed() {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1L));

        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(false);
        when(userRepository.existsByUsername("superadmin")).thenReturn(false);
        when(userRepository.existsByEmail("superadmin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
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

        UserResponseDto response = userRegistrationService.signupInitialSuperAdmin(superAdminRequest, "test-token");

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("superadmin");
        assertThat(response.getEmail()).isEqualTo("superadmin@example.com");
        assertThat(response.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(response.getSchoolId()).isNull();

        verify(userRepository).existsByRole(Role.SUPER_ADMIN);
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("signupInitialSuperAdmin throws UnauthorizedOperationException when super admin already exists")
    void signupInitialSuperAdmin_WhenSuperAdminAlreadyExists_ShouldThrowUnauthorizedOperationException() {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1L));

        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> userRegistrationService.signupInitialSuperAdmin(superAdminRequest, "test-token"))
                .isInstanceOf(UnauthorizedOperationException.class)
                .hasMessageContaining("Registration is disabled. A Super Admin already exists");

        verify(userRepository).existsByRole(Role.SUPER_ADMIN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createSuperAdmin by logged-in SUPER_ADMIN succeeds")
    void createSuperAdmin_WhenCreatorIsSuperAdmin_ShouldSucceed() {
        when(userRepository.existsByUsername("superadmin")).thenReturn(false);
        when(userRepository.existsByEmail("superadmin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
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
        assertThat(response.getSchoolId()).isNull();
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("createSuperAdmin by non-SUPER_ADMIN throws UnauthorizedOperationException")
    void createSuperAdmin_WhenCreatorIsNotSuperAdmin_ShouldThrowUnauthorizedOperationException() {
        assertThatThrownBy(() -> userRegistrationService.createSuperAdmin(superAdminRequest, schoolAdminPrincipal))
                .isInstanceOf(UnauthorizedOperationException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTeacher succeeds with school context")
    void createTeacher_WithSchoolContext_ShouldSucceed() {
        CreateSchoolUserRequestDto request = new CreateSchoolUserRequestDto();
        request.setFirstName("John");
        request.setLastName("Teacher");
        request.setUsername("jteacher");
        request.setEmail("jteacher@example.com");
        request.setPassword("Secret123!");

        doNothing().when(planGuard).assertTeacherCapacity(anyLong(), anyInt());
        when(userRepository.existsByUsername("jteacher")).thenReturn(false);
        when(userRepository.existsByEmail("jteacher@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
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

        UserResponseDto response = userRegistrationService.createTeacher(request, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.TEACHER);
        assertThat(response.getSchoolId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createStudent succeeds with school context")
    void createStudent_WithSchoolContext_ShouldSucceed() {
        CreateSchoolUserRequestDto request = new CreateSchoolUserRequestDto();
        request.setFirstName("Jane");
        request.setLastName("Student");
        request.setUsername("jstudent");
        request.setEmail("jstudent@example.com");
        request.setPassword("Secret123!");

        when(userRepository.existsByUsername("jstudent")).thenReturn(false);
        when(userRepository.existsByEmail("jstudent@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
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

        UserResponseDto response = userRegistrationService.createStudent(request, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.STUDENT);
        assertThat(response.getSchoolId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createParent succeeds with school context")
    void createParent_WithSchoolContext_ShouldSucceed() {
        CreateSchoolUserRequestDto request = new CreateSchoolUserRequestDto();
        request.setFirstName("Bob");
        request.setLastName("Parent");
        request.setUsername("bparent");
        request.setEmail("bparent@example.com");
        request.setPassword("Secret123!");

        when(userRepository.existsByUsername("bparent")).thenReturn(false);
        when(userRepository.existsByEmail("bparent@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
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

        UserResponseDto response = userRegistrationService.createParent(request, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.PARENT);
        assertThat(response.getSchoolId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createLibrarian succeeds with school context and LIBRARY module")
    void createLibrarian_WithSchoolContext_ShouldSucceed() {
        CreateSchoolUserRequestDto request = new CreateSchoolUserRequestDto();
        request.setFirstName("Lib");
        request.setLastName("rarian");
        request.setUsername("librarian");
        request.setEmail("librarian@example.com");
        request.setPassword("Secret123!");

        doNothing().when(moduleAccessService).assertModuleEnabled(eq(10L), any());
        when(userRepository.existsByUsername("librarian")).thenReturn(false);
        when(userRepository.existsByEmail("librarian@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
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

        UserResponseDto response = userRegistrationService.createLibrarian(request, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.LIBRARIAN);
        assertThat(response.getSchoolId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createAccountant succeeds with school context and BILLING module")
    void createAccountant_WithSchoolContext_ShouldSucceed() {
        CreateSchoolUserRequestDto request = new CreateSchoolUserRequestDto();
        request.setFirstName("Acc");
        request.setLastName("ountant");
        request.setUsername("accountant");
        request.setEmail("accountant@example.com");
        request.setPassword("Secret123!");

        doNothing().when(moduleAccessService).assertModuleEnabled(eq(10L), any());
        when(userRepository.existsByUsername("accountant")).thenReturn(false);
        when(userRepository.existsByEmail("accountant@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
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

        UserResponseDto response = userRegistrationService.createAccountant(request, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.ACCOUNTANT);
        assertThat(response.getSchoolId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Role.requiresSameSchool returns true for school-scoped roles")
    void requiresSameSchool_SchoolScopedRoles_ShouldReturnTrue() {
        assertThat(Role.TEACHER.requiresSameSchool()).isTrue();
        assertThat(Role.STUDENT.requiresSameSchool()).isTrue();
        assertThat(Role.PARENT.requiresSameSchool()).isTrue();
        assertThat(Role.LIBRARIAN.requiresSameSchool()).isTrue();
        assertThat(Role.ACCOUNTANT.requiresSameSchool()).isTrue();
    }

    @Test
    @DisplayName("Role.requiresSameSchool returns false for non-school-scoped roles")
    void requiresSameSchool_NonSchoolScopedRoles_ShouldReturnFalse() {
        assertThat(Role.SUPER_ADMIN.requiresSameSchool()).isFalse();
        assertThat(Role.SCHOOL_ADMIN.requiresSameSchool()).isFalse();
    }
}
