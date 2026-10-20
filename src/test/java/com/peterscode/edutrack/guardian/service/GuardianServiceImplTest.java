package com.peterscode.edutrack.guardian.service;

import com.peterscode.edutrack.guardian.dto.CreateGuardianRequestDto;
import com.peterscode.edutrack.guardian.dto.GuardianResponseDto;
import com.peterscode.edutrack.guardian.entity.Guardian;
import com.peterscode.edutrack.guardian.mapper.GuardianMapper;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;
import com.peterscode.edutrack.shared.exception.SchoolInactiveException;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuardianServiceImplTest {

    @Mock
    private GuardianRepository repo;

    @Mock
    private SchoolRepository schools;

    @Mock
    private UserRepository users;

    @Spy
    private GuardianMapper mapper;

    @InjectMocks
    private GuardianServiceImpl service;

    private School sampleSchool;
    private UserPrincipal adminPrincipal;
    private CreateGuardianRequestDto createDto;
    private Guardian sampleGuardian;

    @BeforeEach
    void setUp() {
        sampleSchool = School.builder()
                .name("Alpha Academy")
                .active(true)
                .build();
        sampleSchool.setId(1L);

        adminPrincipal = UserPrincipal.builder()
                .id(2L)
                .email("admin@alpha.edu")
                .schoolId(1L)
                .role(Role.SCHOOL_ADMIN)
                .active(true)
                .build();

        createDto = CreateGuardianRequestDto.builder()
                .schoolId(1L)
                .firstName("Robert")
                .lastName("Smith")
                .email("robert.smith@alpha.edu")
                .phone("+1234567890")
                .relationship("Father")
                .build();

        sampleGuardian = Guardian.builder()
                .schoolId(1L)
                .userId(20L)
                .firstName("Robert")
                .lastName("Smith")
                .email("robert.smith@alpha.edu")
                .phone("+1234567890")
                .relationship("Father")
                .active(true)
                .build();
        sampleGuardian.setId(10L);
    }

    @Test
    @DisplayName("create guardian succeeds for school admin")
    void create_Success() {
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(repo.existsBySchoolIdAndEmail(1L, "robert.smith@alpha.edu")).thenReturn(false);
        when(repo.save(any(Guardian.class))).thenReturn(sampleGuardian);

        GuardianResponseDto result = service.create(createDto, adminPrincipal);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getFirstName()).isEqualTo("Robert");
        verify(repo).save(any(Guardian.class));
    }

    @Test
    @DisplayName("create guardian fails when school is inactive")
    void create_InactiveSchool_ThrowsException() {
        sampleSchool.setActive(false);
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));

        assertThatThrownBy(() -> service.create(createDto, adminPrincipal))
                .isInstanceOf(SchoolInactiveException.class);
    }

    @Test
    @DisplayName("create guardian throws DuplicateResourceException on duplicate email")
    void create_DuplicateEmail_ThrowsException() {
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(repo.existsBySchoolIdAndEmail(1L, "robert.smith@alpha.edu")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createDto, adminPrincipal))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("create guardian with foreign school userId throws ResourceNotFoundException")
    void create_ForeignUserId_ThrowsException() {
        createDto.setUserId(99L);
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(repo.existsBySchoolIdAndEmail(1L, "robert.smith@alpha.edu")).thenReturn(false);

        User foreignUser = User.builder().schoolId(2L).role(Role.PARENT).build();
        foreignUser.setId(99L);
        when(users.findById(99L)).thenReturn(Optional.of(foreignUser));

        assertThatThrownBy(() -> service.create(createDto, adminPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("get guardian succeeds for school admin")
    void get_Success() {
        when(repo.findById(10L)).thenReturn(Optional.of(sampleGuardian));

        GuardianResponseDto result = service.get(10L, adminPrincipal);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
    }
}
