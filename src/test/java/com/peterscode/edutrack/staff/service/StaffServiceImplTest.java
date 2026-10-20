package com.peterscode.edutrack.staff.service;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;
import com.peterscode.edutrack.shared.exception.SchoolInactiveException;
import com.peterscode.edutrack.staff.dto.CreateStaffRequestDto;
import com.peterscode.edutrack.staff.dto.StaffResponseDto;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.enums.StaffStatus;
import com.peterscode.edutrack.staff.mapper.StaffMapper;
import com.peterscode.edutrack.staff.repository.StaffRepository;
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
class StaffServiceImplTest {

    @Mock
    private StaffRepository repo;

    @Mock
    private SchoolRepository schools;

    @Mock
    private UserRepository users;

    @Spy
    private StaffMapper mapper;

    @InjectMocks
    private StaffServiceImpl service;

    private School sampleSchool;
    private UserPrincipal adminPrincipal;
    private UserPrincipal teacherPrincipal;
    private CreateStaffRequestDto createDto;
    private Staff sampleStaff;

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

        teacherPrincipal = UserPrincipal.builder()
                .id(15L)
                .email("teacher@alpha.edu")
                .schoolId(1L)
                .role(Role.TEACHER)
                .active(true)
                .build();

        createDto = CreateStaffRequestDto.builder()
                .schoolId(1L)
                .staffNumber("STF-001")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@alpha.edu")
                .department("Science")
                .designation("Teacher")
                .build();

        sampleStaff = Staff.builder()
                .schoolId(1L)
                .userId(15L)
                .staffNumber("STF-001")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@alpha.edu")
                .department("Science")
                .status(StaffStatus.ACTIVE)
                .active(true)
                .build();
        sampleStaff.setId(10L);
    }

    @Test
    @DisplayName("create staff succeeds for school admin")
    void create_Success() {
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(repo.existsBySchoolIdAndStaffNumber(1L, "STF-001")).thenReturn(false);
        when(repo.existsBySchoolIdAndEmail(1L, "jane.smith@alpha.edu")).thenReturn(false);
        when(repo.save(any(Staff.class))).thenReturn(sampleStaff);

        StaffResponseDto result = service.create(createDto, adminPrincipal);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStaffNumber()).isEqualTo("STF-001");
        verify(repo).save(any(Staff.class));
    }

    @Test
    @DisplayName("create staff fails when school is inactive")
    void create_InactiveSchool_ThrowsException() {
        sampleSchool.setActive(false);
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));

        assertThatThrownBy(() -> service.create(createDto, adminPrincipal))
                .isInstanceOf(SchoolInactiveException.class);
    }

    @Test
    @DisplayName("create staff throws DuplicateResourceException on duplicate staff number")
    void create_DuplicateStaffNumber_ThrowsException() {
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(repo.existsBySchoolIdAndStaffNumber(1L, "STF-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createDto, adminPrincipal))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("create staff with foreign school userId throws ResourceNotFoundException")
    void create_ForeignUserId_ThrowsException() {
        createDto.setUserId(99L);
        when(schools.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(repo.existsBySchoolIdAndStaffNumber(1L, "STF-001")).thenReturn(false);
        when(repo.existsBySchoolIdAndEmail(1L, "jane.smith@alpha.edu")).thenReturn(false);

        User foreignUser = User.builder().schoolId(2L).role(Role.TEACHER).build();
        foreignUser.setId(99L);
        when(users.findById(99L)).thenReturn(Optional.of(foreignUser));

        assertThatThrownBy(() -> service.create(createDto, adminPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("get staff succeeds for teacher accessing own record")
    void get_TeacherOwnRecord_Success() {
        when(repo.findById(10L)).thenReturn(Optional.of(sampleStaff));

        StaffResponseDto result = service.get(10L, teacherPrincipal);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("get staff throws ResourceNotFoundException when teacher accesses another staff record")
    void get_TeacherOtherRecord_ThrowsNotFound() {
        sampleStaff.setUserId(999L); // Different user id
        when(repo.findById(10L)).thenReturn(Optional.of(sampleStaff));

        assertThatThrownBy(() -> service.get(10L, teacherPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
