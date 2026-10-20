package com.peterscode.edutrack.student.service;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.dto.*;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.security.PlanLimitReachedException;
import com.peterscode.edutrack.student.exception.StudentNotFoundException;
import com.peterscode.edutrack.student.mapper.StudentMapper;
import com.peterscode.edutrack.student.repository.StudentRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.peterscode.edutrack.security.PlanGuard planGuard;

    @Mock
    private com.peterscode.edutrack.classlevel.repository.ClassLevelRepository classLevelRepository;

    @Mock
    private com.peterscode.edutrack.classlevel.repository.StreamRepository streamRepository;

    @Mock
    private com.peterscode.edutrack.academicyear.repository.AcademicYearRepository academicYearRepository;

    @Mock
    private com.peterscode.edutrack.student.audit.StudentAuditService auditService;

    @Spy
    private StudentMapper studentMapper = new StudentMapper();

    @InjectMocks
    private StudentServiceImpl studentService;

    private School sampleSchool;
    private Student sampleStudent;
    private UserPrincipal schoolAdminPrincipal;
    private UserPrincipal superAdminPrincipal;
    private UserPrincipal studentPrincipal;
    private CreateStudentRequestDto createDto;

    @BeforeEach
    void setUp() {
        sampleSchool = School.builder()
                .name("Alpha Academy")
                .code("ALPHA")
                .subdomain("alpha")
                .subscriptionTier(SubscriptionTier.STANDARD)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(new HashSet<>(Set.of(ServiceModule.STUDENT_MANAGEMENT, ServiceModule.ATTENDANCE)))
                .maxStudents(500)
                .active(true)
                .build();
        sampleSchool.setId(1L);

        sampleStudent = Student.builder()
                .schoolId(1L)
                .userId(10L)
                .admissionNumber("ADM-001")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@alpha.edu")
                .gradeLevel("Grade 10")
                .section("A")
                .status(StudentStatus.ACTIVE)
                .active(true)
                .build();
        sampleStudent.setId(100L);

        schoolAdminPrincipal = UserPrincipal.builder()
                .id(2L)
                .email("admin@alpha.edu")
                .schoolId(1L)
                .role(Role.SCHOOL_ADMIN)
                .active(true)
                .build();

        superAdminPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("superadmin@edutrack.com")
                .schoolId(0L)
                .role(Role.SUPER_ADMIN)
                .active(true)
                .build();

        studentPrincipal = UserPrincipal.builder()
                .id(10L)
                .email("john.doe@alpha.edu")
                .schoolId(1L)
                .role(Role.STUDENT)
                .active(true)
                .build();

        createDto = CreateStudentRequestDto.builder()
                .admissionNumber("ADM-001")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@alpha.edu")
                .gradeLevel("Grade 10")
                .section("A")
                .status(StudentStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("createStudent succeeds for School Admin in their school")
    void createStudent_SchoolAdmin_Success() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(studentRepository.existsBySchoolIdAndAdmissionNumber(1L, "ADM-001")).thenReturn(false);
        when(studentRepository.existsBySchoolIdAndEmail(1L, "john.doe@alpha.edu")).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> {
            Student s = invocation.getArgument(0);
            s.setId(100L);
            return s;
        });

        StudentResponseDto response = studentService.createStudent(createDto, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getAdmissionNumber()).isEqualTo("ADM-001");
        assertThat(response.getFullName()).isEqualTo("John Doe");
        assertThat(response.getSchoolId()).isEqualTo(1L);
        verify(studentRepository).saveAndFlush(any(Student.class));
    }

    @Test
    @DisplayName("createStudent succeeds for Super Admin specifying schoolId")
    void createStudent_SuperAdmin_Success() {
        createDto.setSchoolId(1L);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(studentRepository.existsBySchoolIdAndAdmissionNumber(1L, "ADM-001")).thenReturn(false);
        when(studentRepository.existsBySchoolIdAndEmail(1L, "john.doe@alpha.edu")).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> {
            Student s = invocation.getArgument(0);
            s.setId(101L);
            return s;
        });

        StudentResponseDto response = studentService.createStudent(createDto, superAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        verify(studentRepository).saveAndFlush(any(Student.class));
    }

    @Test
    @DisplayName("createStudent throws DuplicateResourceException on duplicate admission number")
    void createStudent_DuplicateAdmissionNumber_ThrowsException() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(studentRepository.existsBySchoolIdAndAdmissionNumber(1L, "ADM-001")).thenReturn(true);

        assertThatThrownBy(() -> studentService.createStudent(createDto, schoolAdminPrincipal))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Student already exists with admissionNumber: 'ADM-001'");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createStudent throws PlanLimitReachedException when student quota is reached")
    void createStudent_QuotaExceeded_ThrowsException() {
        doThrow(new PlanLimitReachedException("Student plan limit reached", "LIMIT_REACHED"))
                .when(planGuard).assertStudentCapacity(1L, 1);

        assertThatThrownBy(() -> studentService.createStudent(createDto, schoolAdminPrincipal))
                .isInstanceOf(PlanLimitReachedException.class)
                .hasMessageContaining("Student plan limit reached");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("getStudentById succeeds when requested within tenant")
    void getStudentById_Success() {
        when(studentRepository.findBySchoolIdAndId(1L, 100L)).thenReturn(Optional.of(sampleStudent));

        StudentResponseDto response = studentService.getStudentById(100L, schoolAdminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getAdmissionNumber()).isEqualTo("ADM-001");
    }

    @Test
    @DisplayName("getStudentById throws UnauthorizedOperationException for different tenant")
    void getStudentById_DifferentTenant_ThrowsException() {
        when(studentRepository.findBySchoolIdAndId(99L, 100L)).thenReturn(Optional.empty());
        UserPrincipal otherSchoolAdmin = UserPrincipal.builder()
                .id(5L)
                .schoolId(99L)
                .role(Role.SCHOOL_ADMIN)
                .active(true)
                .build();

        assertThatThrownBy(() -> studentService.getStudentById(100L, otherSchoolAdmin))
                .isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    @DisplayName("getStudentById succeeds for PARENT user when email matches student guardianEmail")
    void getStudentById_ParentUser_MatchingEmail_Success() {
        sampleStudent.setGuardianEmail("parent@alpha.edu");
        when(studentRepository.findBySchoolIdAndId(1L, 100L)).thenReturn(Optional.of(sampleStudent));

        UserPrincipal parentPrincipal = UserPrincipal.builder()
                .id(20L)
                .email("parent@alpha.edu")
                .schoolId(1L)
                .role(Role.PARENT)
                .active(true)
                .build();

        StudentResponseDto response = studentService.getStudentById(100L, parentPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getStudentById throws StudentNotFoundException for PARENT user when email does not match")
    void getStudentById_ParentUser_MismatchEmail_ThrowsNotFound() {
        sampleStudent.setGuardianEmail("other.parent@alpha.edu");
        when(studentRepository.findBySchoolIdAndId(1L, 100L)).thenReturn(Optional.of(sampleStudent));

        UserPrincipal parentPrincipal = UserPrincipal.builder()
                .id(20L)
                .email("unrelated.parent@alpha.edu")
                .schoolId(1L)
                .role(Role.PARENT)
                .active(true)
                .build();

        assertThatThrownBy(() -> studentService.getStudentById(100L, parentPrincipal))
                .isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    @DisplayName("getCurrentStudentProfile succeeds for logged in student user")
    void getCurrentStudentProfile_Success() {
        when(studentRepository.findBySchoolIdAndUserId(1L, 10L)).thenReturn(Optional.of(sampleStudent));

        StudentResponseDto response = studentService.getCurrentStudentProfile(studentPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(10L);
        assertThat(response.getAdmissionNumber()).isEqualTo("ADM-001");
    }

    @Test
    @DisplayName("updateStudent modifies mutable fields successfully")
    void updateStudent_Success() {
        when(studentRepository.findBySchoolIdAndId(1L, 100L)).thenReturn(Optional.of(sampleStudent));
        when(studentRepository.existsBySchoolIdAndEmailAndIdNot(1L, "updated@alpha.edu", 100L)).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStudentRequestDto updateDto = UpdateStudentRequestDto.builder()
                .firstName("Johnny")
                .lastName("Doe")
                .email("updated@alpha.edu")
                .gradeLevel("Grade 11")
                .build();

        StudentResponseDto response = studentService.updateStudent(100L, updateDto, schoolAdminPrincipal);

        assertThat(response.getFirstName()).isEqualTo("Johnny");
        assertThat(response.getEmail()).isEqualTo("updated@alpha.edu");
        assertThat(response.getGradeLevel()).isEqualTo("Grade 11");
    }

    @Test
    @DisplayName("updateStudentStatus updates student status and active flag")
    void updateStudentStatus_Success() {
        when(studentRepository.findBySchoolIdAndId(1L, 100L)).thenReturn(Optional.of(sampleStudent));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStudentStatusRequestDto statusDto = UpdateStudentStatusRequestDto.builder()
                .status(StudentStatus.GRADUATED)
                .build();

        StudentResponseDto response = studentService.updateStudentStatus(100L, statusDto, schoolAdminPrincipal);

        assertThat(response.getStatus()).isEqualTo(StudentStatus.GRADUATED);
        assertThat(response.isActive()).isFalse();
    }

    @Test
    @DisplayName("deleteStudent soft-deletes student")
    void deleteStudent_SoftDeletesStudent() {
        when(studentRepository.findBySchoolIdAndId(1L, 100L)).thenReturn(Optional.of(sampleStudent));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentService.deleteStudent(100L, schoolAdminPrincipal);

        assertThat(sampleStudent.isActive()).isFalse();
        assertThat(sampleStudent.getStatus()).isEqualTo(StudentStatus.WITHDRAWN);
        verify(studentRepository).save(sampleStudent);
    }

    @Test
    @DisplayName("searchStudents returns paginated PageResponse")
    void searchStudents_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(studentRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleStudent), pageable, 1));

        StudentSearchCriteria criteria = StudentSearchCriteria.builder()
                .search("John")
                .build();

        PageResponse<StudentSummaryDto> response = studentService.searchStudents(criteria, pageable, schoolAdminPrincipal);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getFullName()).isEqualTo("John Doe");
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getStudentStatistics returns statistics metrics")
    void getStudentStatistics_Success() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(sampleSchool));
        when(planGuard.effectiveStudentLimit(sampleSchool)).thenReturn(500);
        when(studentRepository.countBySchoolId(1L)).thenReturn(100L);
        when(studentRepository.countBySchoolIdAndStatus(1L, StudentStatus.ACTIVE)).thenReturn(90L);
        when(studentRepository.countGroupedByStatus(1L))
                .thenReturn(java.util.Collections.singletonList(new Object[]{StudentStatus.ACTIVE, 90L}));
        when(studentRepository.countGroupedByGender(1L))
                .thenReturn(java.util.Collections.singletonList(new Object[]{sampleStudent.getGender(), 90L}));
        when(studentRepository.countGroupedByGradeLevel(1L))
                .thenReturn(java.util.Collections.singletonList(new Object[]{sampleStudent.getGradeLevel(), 100L}));

        StudentStatsDto stats = studentService.getStudentStatistics(1L, schoolAdminPrincipal);

        assertThat(stats.getTotalStudents()).isEqualTo(100L);
        assertThat(stats.getActiveStudents()).isEqualTo(90L);
        assertThat(stats.getInactiveStudents()).isEqualTo(10L);
        assertThat(stats.getMaxCapacity()).isEqualTo(500);
        assertThat(stats.getCapacityRemaining()).isEqualTo(410L);
    }

    @Test
    @DisplayName("linkUserAccount links valid student user to student entity")
    void linkUserAccount_Success() {
        User user = User.builder()
                .schoolId(1L)
                .role(Role.STUDENT)
                .email("studentuser@alpha.edu")
                .build();
        user.setId(25L);

        when(studentRepository.findBySchoolIdAndId(1L, 100L)).thenReturn(Optional.of(sampleStudent));
        when(userRepository.findById(25L)).thenReturn(Optional.of(user));
        when(studentRepository.findByUserId(25L)).thenReturn(Optional.empty());
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentResponseDto response = studentService.linkUserAccount(100L, 25L, schoolAdminPrincipal);

        assertThat(response.getUserId()).isEqualTo(25L);
        verify(studentRepository).saveAndFlush(sampleStudent);
    }
}
