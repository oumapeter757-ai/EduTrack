package com.peterscode.edutrack.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.peterscode.edutrack.attendance.dto.AttendanceRequests.BatchRequest;
import com.peterscode.edutrack.attendance.dto.AttendanceRequests.StatusException;
import com.peterscode.edutrack.attendance.entity.AttendanceStatus;
import com.peterscode.edutrack.attendance.entity.AttendanceType;
import com.peterscode.edutrack.attendance.entity.StudentAttendance;
import com.peterscode.edutrack.attendance.exception.AttendanceRuleException;
import com.peterscode.edutrack.attendance.repository.StudentAttendanceRepository;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.enums.StaffStatus;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.entity.Timetable;
import com.peterscode.edutrack.timetable.entity.TimetableLesson;
import com.peterscode.edutrack.timetable.repository.TermRepository;
import com.peterscode.edutrack.timetable.repository.TimetableLessonRepository;
import com.peterscode.edutrack.timetable.repository.TimetableRepository;
import com.peterscode.edutrack.user.entity.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
    @Mock private SchoolRepository schoolRepository;
    @Mock private StreamRepository streamRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentAttendanceRepository attendanceRepository;
    @Mock private TermRepository termRepository;
    @Mock private TimetableRepository timetableRepository;
    @Mock private TimetableLessonRepository lessonRepository;
    @Mock private StaffRepository staffRepository;
    @InjectMocks private AttendanceService service;

    @Test
    void recordBatchDefaultsUnmentionedStudentsToPresentAndUpdatesWholeRoster() {
        LocalDate today = LocalDate.now();
        UserPrincipal principal = principal(Role.SCHOOL_ADMIN, 1L, 11L);
        Stream stream = Stream.builder().schoolId(1L).active(true).build();
        stream.setId(21L);
        Term term = Term.builder().schoolId(1L).active(true)
                .startDate(today.minusDays(10)).endDate(today.plusDays(10)).build();
        term.setId(31L);
        Student absent = student(41L, "Ari", "A");
        Student present = student(42L, "Bo", "B");

        when(streamRepository.findLockedBySchoolIdAndId(1L, 21L)).thenReturn(Optional.of(stream));
        when(termRepository.findBySchoolIdAndIdAndActiveTrue(1L, 31L)).thenReturn(Optional.of(term));
        when(studentRepository.findBySchoolIdAndStreamIdAndActiveTrueOrderByFirstNameAscLastNameAsc(1L, 21L))
                .thenReturn(List.of(absent, present));
        StudentAttendance existingAbsent = StudentAttendance.builder().schoolId(1L).termId(31L).streamId(21L)
                .studentId(41L).attendanceDate(today).attendanceType(AttendanceType.DAILY)
                .status(AttendanceStatus.PRESENT).build();
        existingAbsent.setId(51L);
        when(attendanceRepository.findBySchoolIdAndTermIdAndStreamIdAndAttendanceDateAndAttendanceTypeAndTimetableLessonId(
                1L, 31L, 21L, today, AttendanceType.DAILY, null)).thenReturn(List.of(existingAbsent));
        when(attendanceRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BatchRequest request = new BatchRequest(1L, 31L, 21L, today, AttendanceType.DAILY, null,
                List.of(new StatusException(41L, AttendanceStatus.ABSENT, "Sick")));
        var result = service.recordBatch(request, principal);

        assertThat(result.rosterSize()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.attendance()).extracting("status")
                .containsExactly(AttendanceStatus.ABSENT, AttendanceStatus.PRESENT);
        assertThat(result.attendance()).extracting("remarks").containsExactly("Sick", null);
        verify(attendanceRepository).saveAllAndFlush(anyList());
    }

    @Test
    void myAttendanceOnlyLoadsTheAuthenticatedStudentsSchoolScopedRecords() {
        UserPrincipal principal = principal(Role.STUDENT, 1L, 100L);
        Student student = student(41L, "Ari", "A");
        student.setUserId(100L);
        when(studentRepository.findBySchoolIdAndUserId(1L, 100L)).thenReturn(Optional.of(student));
        when(attendanceRepository.findBySchoolIdAndStudentIdOrderByAttendanceDateDescIdDesc(1L, 41L))
                .thenReturn(List.of());

        assertThat(service.myAttendance(principal)).isEmpty();
        verify(attendanceRepository).findBySchoolIdAndStudentIdOrderByAttendanceDateDescIdDesc(1L, 41L);
        verifyNoInteractions(termRepository, timetableRepository, lessonRepository);
    }

    @Test
    void teacherCannotRecordPeriodAttendanceForAnotherTeachersLesson() {
        LocalDate today = LocalDate.now();
        UserPrincipal principal = principal(Role.TEACHER, 1L, 100L);
        Stream stream = Stream.builder().schoolId(1L).active(true).build();
        stream.setId(21L);
        Term term = Term.builder().schoolId(1L).active(true)
                .startDate(today.minusDays(10)).endDate(today.plusDays(10)).build();
        term.setId(31L);
        TimetableLesson lesson = TimetableLesson.builder().schoolId(1L).timetableId(71L)
                .streamId(21L).teacherId(99L).dayOfWeek(today.getDayOfWeek().getValue()).build();
        lesson.setId(61L);
        Timetable timetable = Timetable.builder().schoolId(1L).termId(31L)
                .status(Timetable.TimetableStatus.PUBLISHED).build();
        Staff teacher = Staff.builder().schoolId(1L).userId(100L).active(true)
                .status(StaffStatus.ACTIVE).build();
        teacher.setId(45L);

        when(streamRepository.findLockedBySchoolIdAndId(1L, 21L)).thenReturn(Optional.of(stream));
        when(termRepository.findBySchoolIdAndIdAndActiveTrue(1L, 31L)).thenReturn(Optional.of(term));
        when(lessonRepository.findBySchoolIdAndId(1L, 61L)).thenReturn(Optional.of(lesson));
        when(timetableRepository.findLocked(1L, 71L)).thenReturn(Optional.of(timetable));
        when(staffRepository.findBySchoolIdAndUserId(1L, 100L)).thenReturn(Optional.of(teacher));

        BatchRequest request = new BatchRequest(1L, 31L, 21L, today, AttendanceType.PERIOD, 61L, List.of());
        assertThatThrownBy(() -> service.recordBatch(request, principal))
                .isInstanceOf(AttendanceRuleException.class)
                .hasMessage("Teacher is not assigned to this timetable lesson");
        verify(attendanceRepository, never()).saveAllAndFlush(anyList());
    }

    private UserPrincipal principal(Role role, Long schoolId, Long userId) {
        return UserPrincipal.builder().id(userId).schoolId(schoolId).role(role).active(true).build();
    }

    private Student student(Long id, String firstName, String lastName) {
        Student student = Student.builder().schoolId(1L).streamId(21L).firstName(firstName)
                .lastName(lastName).admissionNumber("S-" + id).active(true).build();
        student.setId(id);
        return student;
    }
}
