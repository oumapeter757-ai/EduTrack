package com.peterscode.edutrack.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.peterscode.edutrack.attendance.dto.AttendanceRequests.BatchRequest;
import com.peterscode.edutrack.attendance.entity.AttendanceType;
import com.peterscode.edutrack.attendance.exception.AttendanceRuleException;
import com.peterscode.edutrack.attendance.repository.StudentAttendanceRepository;
import com.peterscode.edutrack.attendance.service.AttendanceService;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.ModuleAccessDeniedException;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.enums.StaffStatus;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.repository.TermRepository;
import com.peterscode.edutrack.timetable.repository.TimetableLessonRepository;
import com.peterscode.edutrack.timetable.repository.TimetableRepository;
import com.peterscode.edutrack.user.entity.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;

@ExtendWith(MockitoExtension.class)
class AttendanceAttackTest {

    @Mock private SchoolRepository schoolRepository;
    @Mock private StreamRepository streamRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentAttendanceRepository attendanceRepository;
    @Mock private TermRepository termRepository;
    @Mock private TimetableRepository timetableRepository;
    @Mock private TimetableLessonRepository lessonRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private StreamSubjectRepository streamSubjectRepository;
    @Mock private ModuleAccessService moduleAccessService;
    @Mock private Clock clock;

    @InjectMocks private AttendanceService service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-31T08:00:00Z"), ZoneId.of("UTC"));
    private final LocalDate today = LocalDate.now(fixedClock);

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-10-31T08:00:00Z"));
        lenient().when(clock.withZone(any())).thenReturn(fixedClock);
    }

    @Test
    @DisplayName("ATTACK 1: Period attendance must verify TIMETABLE module in AttendanceService")
    void periodAttendanceRequiresTimetableModule() {
        Long schoolId = 1L;
        UserPrincipal principal = UserPrincipal.builder()
                .id(10L).schoolId(schoolId).role(Role.TEACHER).active(true).build();

        doThrow(new ModuleAccessDeniedException(schoolId, 10L, "TIMETABLE", "MODULE_NOT_IN_PLAN", "Module is not enabled"))
                .when(moduleAccessService).assertModuleEnabled(schoolId, ServiceModule.TIMETABLE);

        BatchRequest request = new BatchRequest(schoolId, 30L, 20L, today, AttendanceType.PERIOD, 100L, List.of());

        assertThatThrownBy(() -> service.recordBatch(request, principal))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MODULE_NOT_IN_PLAN");

        verify(moduleAccessService).assertModuleEnabled(schoolId, ServiceModule.TIMETABLE);
    }

    @Test
    @DisplayName("ATTACK 2: AttendanceService must inject java.time.Clock for deterministic time & timezone handling")
    void attendanceServiceMustInjectClock() {
        boolean hasClock = false;
        for (var field : AttendanceService.class.getDeclaredFields()) {
            if (field.getType().equals(java.time.Clock.class)) {
                hasClock = true;
                break;
            }
        }
        assertThat(hasClock)
                .as("AttendanceService must inject java.time.Clock instead of calling static LocalDate.now()")
                .isTrue();
    }

    @Test
    @DisplayName("ATTACK 3: Future-date attendance recording must be rejected for teachers")
    void futureDateAttendanceMustBeRejected() {
        Long schoolId = 1L;
        UserPrincipal principal = UserPrincipal.builder()
                .id(10L).schoolId(schoolId).role(Role.TEACHER).active(true).build();

        LocalDate futureDate = today.plusDays(2);
        Stream stream = Stream.builder().schoolId(schoolId).active(true).build();
        stream.setId(20L);
        Term term = Term.builder().schoolId(schoolId).active(true)
                .startDate(today.minusDays(5)).endDate(futureDate.plusDays(10)).build();
        term.setId(30L);
        Staff staff = Staff.builder().schoolId(schoolId).userId(10L).status(StaffStatus.ACTIVE).active(true).build();
        staff.setId(40L);

        when(streamRepository.findLockedBySchoolIdAndId(schoolId, 20L)).thenReturn(Optional.of(stream));
        when(termRepository.findBySchoolIdAndIdAndActiveTrue(schoolId, 30L)).thenReturn(Optional.of(term));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(School.builder().timezone("UTC").build()));

        BatchRequest request = new BatchRequest(schoolId, 30L, 20L, futureDate, AttendanceType.DAILY, null, List.of());

        assertThatThrownBy(() -> service.recordBatch(request, principal))
                .as("Teachers must not be allowed to record attendance for future dates")
                .isInstanceOf(AttendanceRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "FUTURE_ATTENDANCE_LOCKED");
    }

    @Test
    @DisplayName("ATTACK 4: Teacher at FREE tier school without timetable must be able to record DAILY attendance if assigned to stream")
    void teacherCanRecordDailyAttendanceOnFreeTierWithoutTimetable() {
        Long schoolId = 1L;
        UserPrincipal principal = UserPrincipal.builder()
                .id(10L).schoolId(schoolId).role(Role.TEACHER).active(true).build();

        Stream stream = Stream.builder().schoolId(schoolId).active(true).build();
        stream.setId(20L);
        Term term = Term.builder().schoolId(schoolId).academicYearId(5L).active(true)
                .startDate(today.minusDays(5)).endDate(today.plusDays(30)).build();
        term.setId(30L);
        Staff staff = Staff.builder().schoolId(schoolId).userId(10L).status(StaffStatus.ACTIVE).active(true).build();
        staff.setId(40L);
        Student student = Student.builder().schoolId(schoolId).streamId(20L).firstName("John").lastName("Doe").active(true).build();
        student.setId(50L);

        when(streamRepository.findLockedBySchoolIdAndId(schoolId, 20L)).thenReturn(Optional.of(stream));
        when(termRepository.findBySchoolIdAndIdAndActiveTrue(schoolId, 30L)).thenReturn(Optional.of(term));
        when(staffRepository.findBySchoolIdAndUserId(schoolId, 10L)).thenReturn(Optional.of(staff));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(School.builder().timezone("UTC").build()));
        when(studentRepository.findBySchoolIdAndStreamIdAndActiveTrueOrderByFirstNameAscLastNameAsc(schoolId, 20L))
                .thenReturn(List.of(student));
        when(attendanceRepository.findBySchoolIdAndTermIdAndStreamIdAndAttendanceDateAndAttendanceTypeAndTimetableLessonId(
                schoolId, 30L, 20L, today, AttendanceType.DAILY, null)).thenReturn(List.of());
        when(attendanceRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // School does not have TIMETABLE module in plan
        doThrow(new ModuleAccessDeniedException(schoolId, 10L, "TIMETABLE", "MODULE_NOT_IN_PLAN", "Module is not enabled"))
                .when(moduleAccessService).assertModuleEnabled(schoolId, ServiceModule.TIMETABLE);

        // Teacher is assigned to stream via stream_subjects in TEACHER_MANAGEMENT
        when(streamSubjectRepository.existsBySchoolIdAndTeacherIdAndStreamIdAndAcademicYearId(schoolId, 40L, 20L, 5L))
                .thenReturn(true);

        BatchRequest request = new BatchRequest(schoolId, 30L, 20L, today, AttendanceType.DAILY, null, List.of());

        var response = service.recordBatch(request, principal);
        assertThat(response.rosterSize()).isEqualTo(1);
        assertThat(response.createdCount()).isEqualTo(1);
    }
}
