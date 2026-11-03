package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.student.dto.CreateStudentRequestDto;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.student.service.StudentService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for student creation and plan limits.
 * These tests are expected to FAIL on the current code because:
 * - PlanGuard uses a plain SELECT count after FOR UPDATE lock — stale in READ COMMITTED
 * - Duplicate admission number check is check-then-act without lock
 */
@Tag("concurrency")
class StudentConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    private UserPrincipal adminPrincipal;

    @org.junit.jupiter.api.BeforeEach
    void setupStudent() {
        adminPrincipal = UserPrincipal.create(userRepository.findById(adminUserId).orElseThrow());
    }

    /**
     * Test: School at limit-1 students. 10 threads try to create students.
     * Expected: Exactly 1 succeeds, 9 get 403 (plan limit) or 409.
     * Current code: Multiple threads can pass the count check and exceed the limit.
     */
    @Test
    void planLimitRace_shouldNotExceedLimit() throws Exception {
        // Set school limit to 5
        School school = schoolRepository.findById(schoolId).orElseThrow();
        school.setMaxStudents(5);
        schoolRepository.save(school);

        // Create 4 students to reach limit-1
        for (int i = 0; i < 4; i++) {
            CreateStudentRequestDto dto = new CreateStudentRequestDto();
            dto.setSchoolId(schoolId);
            dto.setAdmissionNumber("PRE-" + schoolId + "-" + i);
            dto.setFirstName("Pre");
            dto.setLastName("Student" + i);
            dto.setStatus(StudentStatus.ACTIVE);
            studentService.createStudent(dto, adminPrincipal);
        }

        // Now 10 threads try to create the 5th student
        int threads = 10;
        TestResult<Object> result = runConcurrent(threads, () -> {
            CreateStudentRequestDto dto = new CreateStudentRequestDto();
            dto.setSchoolId(schoolId);
            dto.setAdmissionNumber("RACE-" + schoolId + "-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 100000));
            dto.setFirstName("Race");
            dto.setLastName("Student");
            dto.setStatus(StudentStatus.ACTIVE);
            studentService.createStudent(dto, adminPrincipal);
            return "created";
        }, 30);

        // Count active students
        long activeCount = studentRepository.countBySchoolIdAndStatus(schoolId, StudentStatus.ACTIVE);

        assertThat(activeCount)
                .as("Active students must never exceed the plan limit of 5")
                .isLessThanOrEqualTo(5);

        assertThat(result.successCount)
                .as("Exactly one thread should succeed at limit-1")
                .isEqualTo(1);

        // No unhandled errors
        assertThat(result.errorCount)
                .as("No unhandled errors from plan limit race")
                .isZero();
    }

    /**
     * Test: Two threads create students with the same admission number.
     * Expected: One succeeds, one gets 409.
     * Current code: The unique constraint catches this, but the service check is racy.
     */
    @Test
    void duplicateAdmissionNumber_shouldRejectOne() throws Exception {
        String admissionNumber = "DUP-" + schoolId + "-001";

        TestResult<Object> result = runConcurrent(2, () -> {
            CreateStudentRequestDto dto = new CreateStudentRequestDto();
            dto.setSchoolId(schoolId);
            dto.setAdmissionNumber(admissionNumber);
            dto.setFirstName("Dup");
            dto.setLastName("Student");
            dto.setStatus(StudentStatus.ACTIVE);
            studentService.createStudent(dto, adminPrincipal);
            return "created";
        }, 30);

        // Verify only one student with this admission number
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> admissionNumber.equals(s.getAdmissionNumber()))
                .toList();

        assertThat(students.size())
                .as("Only one student with this admission number")
                .isEqualTo(1);

        assertThat(result.successCount)
                .as("Exactly one thread should succeed")
                .isEqualTo(1);
    }
}
