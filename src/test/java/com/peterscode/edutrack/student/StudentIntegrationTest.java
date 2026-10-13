package com.peterscode.edutrack.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.dto.BulkStudentStatusUpdateRequestDto;
import com.peterscode.edutrack.student.dto.CreateStudentRequestDto;
import com.peterscode.edutrack.student.dto.UpdateStudentRequestDto;
import com.peterscode.edutrack.student.dto.UpdateStudentStatusRequestDto;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StudentIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private UserPrincipal superAdminPrincipal;
    private UserPrincipal schoolAdminPrincipal1;
    private UserPrincipal schoolAdminPrincipal2;
    private UserPrincipal studentUserPrincipal1;
    private UserPrincipal studentUserPrincipal2;
    private UserPrincipal teacherPrincipal;

    private School savedSchool1;
    private School savedSchool2;
    private Student savedStudent1;
    private Student savedStudent2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        studentRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();

        // Seed Super Admin
        User superAdmin = User.builder()
                .firstName("Super")
                .lastName("Admin")
                .username("superadmin")
                .email("superadmin@edutrack.com")
                .password("Password123!")
                .role(Role.SUPER_ADMIN)
                .schoolId(0L)
                .active(true)
                .build();
        superAdmin = userRepository.save(superAdmin);
        superAdminPrincipal = UserPrincipal.create(superAdmin);

        // Seed School 1
        savedSchool1 = School.builder()
                .name("Alpha Academy")
                .code("ALPHA")
                .subdomain("alpha")
                .phone("+1111111111")
                .email("info@alpha.edu")
                .city("Boston")
                .country("USA")
                .timezone("America/New_York")
                .currency("USD")
                .subscriptionTier(SubscriptionTier.STANDARD)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(new HashSet<>(Set.of(ServiceModule.STUDENT_MANAGEMENT, ServiceModule.ATTENDANCE, ServiceModule.GRADING)))
                .maxStudents(100)
                .active(true)
                .build();
        savedSchool1 = schoolRepository.save(savedSchool1);

        // Seed School 2
        savedSchool2 = School.builder()
                .name("Beta Academy")
                .code("BETA")
                .subdomain("beta")
                .phone("+2222222222")
                .email("info@beta.edu")
                .city("Chicago")
                .country("USA")
                .timezone("America/Chicago")
                .currency("USD")
                .subscriptionTier(SubscriptionTier.STANDARD)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .enabledModules(new HashSet<>(Set.of(ServiceModule.STUDENT_MANAGEMENT)))
                .maxStudents(50)
                .active(true)
                .build();
        savedSchool2 = schoolRepository.save(savedSchool2);

        // Seed School Admin for School 1
        User admin1 = User.builder()
                .firstName("Alpha")
                .lastName("Admin")
                .username("alpha_admin")
                .email("admin@alpha.edu")
                .password("Password123!")
                .role(Role.SCHOOL_ADMIN)
                .schoolId(savedSchool1.getId())
                .active(true)
                .build();
        admin1 = userRepository.save(admin1);
        schoolAdminPrincipal1 = UserPrincipal.create(admin1);

        // Seed School Admin for School 2
        User admin2 = User.builder()
                .firstName("Beta")
                .lastName("Admin")
                .username("beta_admin")
                .email("admin@beta.edu")
                .password("Password123!")
                .role(Role.SCHOOL_ADMIN)
                .schoolId(savedSchool2.getId())
                .active(true)
                .build();
        admin2 = userRepository.save(admin2);
        schoolAdminPrincipal2 = UserPrincipal.create(admin2);

        // Seed Teacher for School 1
        User teacher = User.builder()
                .firstName("Teacher")
                .lastName("One")
                .username("teacher1")
                .email("teacher@alpha.edu")
                .password("Password123!")
                .role(Role.TEACHER)
                .schoolId(savedSchool1.getId())
                .active(true)
                .build();
        teacher = userRepository.save(teacher);
        teacherPrincipal = UserPrincipal.create(teacher);

        // Seed Student User 1
        User studentUser1 = User.builder()
                .firstName("John")
                .lastName("Doe")
                .username("johndoe")
                .email("john.doe@alpha.edu")
                .password("Password123!")
                .role(Role.STUDENT)
                .schoolId(savedSchool1.getId())
                .active(true)
                .build();
        studentUser1 = userRepository.save(studentUser1);
        studentUserPrincipal1 = UserPrincipal.create(studentUser1);

        // Seed Student User 2 (in School 2)
        User studentUser2 = User.builder()
                .firstName("Jane")
                .lastName("Smith")
                .username("janesmith")
                .email("jane.smith@beta.edu")
                .password("Password123!")
                .role(Role.STUDENT)
                .schoolId(savedSchool2.getId())
                .active(true)
                .build();
        studentUser2 = userRepository.save(studentUser2);
        studentUserPrincipal2 = UserPrincipal.create(studentUser2);

        // Seed Student record for School 1
        savedStudent1 = Student.builder()
                .schoolId(savedSchool1.getId())
                .userId(studentUser1.getId())
                .admissionNumber("ALPHA-STU-001")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@alpha.edu")
                .gradeLevel("Grade 10")
                .section("A")
                .gender(Gender.MALE)
                .bloodGroup(BloodGroup.O_POSITIVE)
                .status(StudentStatus.ACTIVE)
                .active(true)
                .build();
        savedStudent1 = studentRepository.save(savedStudent1);

        // Seed Student record for School 2
        savedStudent2 = Student.builder()
                .schoolId(savedSchool2.getId())
                .userId(studentUser2.getId())
                .admissionNumber("BETA-STU-001")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@beta.edu")
                .gradeLevel("Grade 11")
                .section("B")
                .gender(Gender.FEMALE)
                .status(StudentStatus.ACTIVE)
                .active(true)
                .build();
        savedStudent2 = studentRepository.save(savedStudent2);
    }

    @Test
    @DisplayName("SCHOOL_ADMIN creates a student for their own school")
    void schoolAdminCanCreateStudent() throws Exception {
        CreateStudentRequestDto request = CreateStudentRequestDto.builder()
                .admissionNumber("ALPHA-STU-002")
                .firstName("Michael")
                .lastName("Johnson")
                .email("michael.j@alpha.edu")
                .gradeLevel("Grade 10")
                .section("B")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(2009, 3, 10))
                .status(StudentStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/v1/students")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.admissionNumber").value("ALPHA-STU-002"))
                .andExpect(jsonPath("$.data.firstName").value("Michael"))
                .andExpect(jsonPath("$.data.schoolId").value(savedSchool1.getId()))
                .andExpect(jsonPath("$.data.enrolled").value(true));

        assertThat(studentRepository.existsBySchoolIdAndAdmissionNumber(savedSchool1.getId(), "ALPHA-STU-002")).isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMIN creates a student by specifying schoolId")
    void superAdminCanCreateStudent() throws Exception {
        CreateStudentRequestDto request = CreateStudentRequestDto.builder()
                .schoolId(savedSchool1.getId())
                .admissionNumber("ALPHA-STU-003")
                .firstName("Sarah")
                .lastName("Connor")
                .email("sarah.c@alpha.edu")
                .gradeLevel("Grade 12")
                .gender(Gender.FEMALE)
                .status(StudentStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/v1/students")
                        .with(user(superAdminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.admissionNumber").value("ALPHA-STU-003"))
                .andExpect(jsonPath("$.data.schoolId").value(savedSchool1.getId()));
    }

    @Test
    @DisplayName("Duplicate admission number within same school returns 409 Conflict")
    void duplicateAdmissionNumberReturnsConflict() throws Exception {
        CreateStudentRequestDto request = CreateStudentRequestDto.builder()
                .admissionNumber("ALPHA-STU-001") // already exists in School 1
                .firstName("Duplicate")
                .lastName("Student")
                .build();

        mockMvc.perform(post("/api/v1/students")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Exceeding school max students quota returns 400 Bad Request")
    void exceedingMaxCapacityReturnsBadRequest() throws Exception {
        savedSchool1.setMaxStudents(1);
        schoolRepository.save(savedSchool1); // Already 1 active student in School 1

        CreateStudentRequestDto request = CreateStudentRequestDto.builder()
                .admissionNumber("ALPHA-STU-099")
                .firstName("Over")
                .lastName("Capacity")
                .build();

        mockMvc.perform(post("/api/v1/students")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("maximum student capacity")));
    }

    @Test
    @DisplayName("Disabled STUDENT_MANAGEMENT module returns 403 Forbidden")
    void disabledModuleReturnsForbidden() throws Exception {
        savedSchool1.setEnabledModules(new HashSet<>(Set.of(ServiceModule.ATTENDANCE)));
        schoolRepository.save(savedSchool1);

        CreateStudentRequestDto request = CreateStudentRequestDto.builder()
                .admissionNumber("ALPHA-STU-100")
                .firstName("No")
                .lastName("Module")
                .build();

        mockMvc.perform(post("/api/v1/students")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not enabled")));
    }

    @Test
    @DisplayName("SCHOOL_ADMIN gets student by ID within own school")
    void schoolAdminGetStudentByIdSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/students/" + savedStudent1.getId())
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(savedStudent1.getId()))
                .andExpect(jsonPath("$.data.admissionNumber").value("ALPHA-STU-001"))
                .andExpect(jsonPath("$.data.fullName").value("John Doe"));
    }

    @Test
    @DisplayName("SCHOOL_ADMIN cannot access student from another school (Tenant Isolation)")
    void schoolAdminCannotAccessOtherSchoolStudent() throws Exception {
        mockMvc.perform(get("/api/v1/students/" + savedStudent2.getId())
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student user can view their own profile via /me")
    void studentCanViewOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/students/me")
                        .with(user(studentUserPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admissionNumber").value("ALPHA-STU-001"))
                .andExpect(jsonPath("$.data.email").value("john.doe@alpha.edu"));
    }

    @Test
    @DisplayName("Student user cannot view another student profile by ID")
    void studentCannotViewOtherStudentProfile() throws Exception {
        mockMvc.perform(get("/api/v1/students/" + savedStudent2.getId())
                        .with(user(studentUserPrincipal1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Get student by admission number")
    void getStudentByAdmissionNumber() throws Exception {
        mockMvc.perform(get("/api/v1/students/admission/ALPHA-STU-001")
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admissionNumber").value("ALPHA-STU-001"))
                .andExpect(jsonPath("$.data.firstName").value("John"));
    }

    @Test
    @DisplayName("Update student details")
    void updateStudent() throws Exception {
        UpdateStudentRequestDto updateDto = UpdateStudentRequestDto.builder()
                .firstName("Johnny")
                .lastName("Doe")
                .email("johnny.doe@alpha.edu")
                .gradeLevel("Grade 10")
                .city("Boston")
                .medicalNotes("Allergic to peanuts")
                .build();

        mockMvc.perform(put("/api/v1/students/" + savedStudent1.getId())
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Johnny"))
                .andExpect(jsonPath("$.data.email").value("johnny.doe@alpha.edu"))
                .andExpect(jsonPath("$.data.medicalNotes").value("Allergic to peanuts"));
    }

    @Test
    @DisplayName("Update student status to GRADUATED deactivates student")
    void updateStudentStatus() throws Exception {
        UpdateStudentStatusRequestDto statusDto = UpdateStudentStatusRequestDto.builder()
                .status(StudentStatus.GRADUATED)
                .build();

        mockMvc.perform(patch("/api/v1/students/" + savedStudent1.getId() + "/status")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADUATED"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.enrolled").value(false));
    }

    @Test
    @DisplayName("Bulk update status for students")
    void bulkUpdateStatus() throws Exception {
        BulkStudentStatusUpdateRequestDto bulkDto = BulkStudentStatusUpdateRequestDto.builder()
                .studentIds(Set.of(savedStudent1.getId()))
                .status(StudentStatus.SUSPENDED)
                .active(false)
                .build();

        mockMvc.perform(post("/api/v1/students/bulk-status")
                        .with(user(schoolAdminPrincipal1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Student updated = studentRepository.findById(savedStudent1.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(StudentStatus.SUSPENDED);
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @DisplayName("Soft delete student marks active=false and status=WITHDRAWN")
    void deleteStudent() throws Exception {
        mockMvc.perform(delete("/api/v1/students/" + savedStudent1.getId())
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Student deleted = studentRepository.findById(savedStudent1.getId()).orElseThrow();
        assertThat(deleted.isActive()).isFalse();
        assertThat(deleted.getStatus()).isEqualTo(StudentStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("Search students with filters and pagination")
    void searchStudents() throws Exception {
        mockMvc.perform(get("/api/v1/students")
                        .with(user(schoolAdminPrincipal1))
                        .param("search", "John")
                        .param("gradeLevel", "Grade 10")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].admissionNumber").value("ALPHA-STU-001"));
    }

    @Test
    @DisplayName("Get student enrollment and capacity statistics")
    void getStudentStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/students/stats")
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(1))
                .andExpect(jsonPath("$.data.activeStudents").value(1))
                .andExpect(jsonPath("$.data.maxCapacity").value(100))
                .andExpect(jsonPath("$.data.capacityRemaining").value(99));
    }

    @Test
    @DisplayName("Teacher can search and view students in their school")
    void teacherCanSearchStudents() throws Exception {
        mockMvc.perform(get("/api/v1/students")
                        .with(user(teacherPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].admissionNumber").value("ALPHA-STU-001"));
    }

    @Test
    @DisplayName("Teacher cannot delete or update students (Forbidden)")
    void teacherCannotModifyStudents() throws Exception {
        mockMvc.perform(delete("/api/v1/students/" + savedStudent1.getId())
                        .with(user(teacherPrincipal)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Link and unlink user login account from student")
    void linkAndUnlinkUser() throws Exception {
        // Create an unlinked student user
        User newStudentUser = User.builder()
                .firstName("Bob")
                .lastName("Marley")
                .username("bobmarley")
                .email("bob@alpha.edu")
                .password("Password123!")
                .role(Role.STUDENT)
                .schoolId(savedSchool1.getId())
                .active(true)
                .build();
        newStudentUser = userRepository.save(newStudentUser);

        // Link
        mockMvc.perform(post("/api/v1/students/" + savedStudent1.getId() + "/link-user/" + newStudentUser.getId())
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(newStudentUser.getId()));

        // Unlink
        mockMvc.perform(post("/api/v1/students/" + savedStudent1.getId() + "/unlink-user")
                        .with(user(schoolAdminPrincipal1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isEmpty());
    }
}
