package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.attendance.entity.StudentAttendance;
import com.peterscode.edutrack.attendance.repository.StudentAttendanceRepository;
import com.peterscode.edutrack.attendance.service.AttendanceService;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for attendance submission.
 * The unique constraint should prevent duplicates, but the service check is racy.
 */
@Tag("concurrency")
class AttendanceConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private StudentAttendanceRepository attendanceRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassLevelRepository classLevelRepository;

    @Autowired
    private StreamRepository streamRepository;

    private Long studentId;
    private Long streamId;

    @org.junit.jupiter.api.BeforeEach
    void setupAttendance() {
        // Create class level and stream
        ClassLevel level = ClassLevel.builder()
                .schoolId(schoolId)
                .name("Grade 1")
                .code("G1")
                .active(true)
                .build();
        level = classLevelRepository.save(level);

        Stream stream = Stream.builder()
                .schoolId(schoolId)
                .classLevelId(level.getId())
                .name("Section A")
                .code("A")
                .active(true)
                .build();
        stream = streamRepository.save(stream);
        streamId = stream.getId();

        // Create a student
        Student student = Student.builder()
                .schoolId(schoolId)
                .firstName("Attend")
                .lastName("Student")
                .admissionNumber("ATT-" + schoolId + "-001")
                .streamId(streamId)
                .status(com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE)
                .build();
        student = studentRepository.save(student);
        studentId = student.getId();
    }

    /**
     * Test: Two threads submit attendance for the same student/session.
     * Expected: One succeeds, one gets 409.
     */
    @Test
    void doubleSubmit_shouldAllowOnlyOne() throws Exception {
        LocalDate today = LocalDate.now();

        // Try to insert attendance records concurrently
        TestResult<Object> result = runConcurrent(2, () -> {
            StudentAttendance attendance = StudentAttendance.builder()
                    .schoolId(schoolId)
                    .studentId(studentId)
                    .streamId(streamId)
                    .attendanceDate(today)
                    .attendanceType(com.peterscode.edutrack.attendance.entity.AttendanceType.DAILY)
                    .status(com.peterscode.edutrack.attendance.entity.AttendanceStatus.PRESENT)
                    .build();
            attendanceRepository.save(attendance);
            return "saved";
        }, 30);

        // Verify only one attendance record exists
        List<StudentAttendance> records = attendanceRepository.findAll().stream()
                .filter(a -> studentId.equals(a.getStudentId()) && today.equals(a.getAttendanceDate()))
                .toList();

        assertThat(records.size())
                .as("Only one attendance record for this student and date")
                .isEqualTo(1);
    }
}
