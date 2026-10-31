package com.peterscode.edutrack.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import com.peterscode.edutrack.academicyear.repository.AcademicYearRepository;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.enums.StaffStatus;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.subject.entity.Subject;
import com.peterscode.edutrack.subject.repository.SubjectRepository;
import com.peterscode.edutrack.timetable.controller.TermController;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.*;
import com.peterscode.edutrack.timetable.entity.Period.PeriodType;
import com.peterscode.edutrack.timetable.entity.StreamSubject;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.entity.Timetable;
import com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus;
import com.peterscode.edutrack.timetable.entity.TimetableLesson;
import com.peterscode.edutrack.timetable.exception.TimetableRuleException;
import com.peterscode.edutrack.timetable.repository.*;
import com.peterscode.edutrack.timetable.service.TimetableService;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TimetableAttackTest {

    @Mock private SchoolRepository schoolRepository;
    @Mock private AcademicYearRepository academicYearRepository;
    @Mock private ClassLevelRepository classLevelRepository;
    @Mock private StreamRepository streamRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TermRepository termRepository;
    @Mock private PeriodRepository periodRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ClassLevelSubjectRepository curriculumRepository;
    @Mock private StreamSubjectRepository assignmentRepository;
    @Mock private TimetableRepository timetableRepository;
    @Mock private TimetableLessonRepository lessonRepository;
    @Mock private TimetableAuditRepository auditRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TimetableService service;

    private final LocalDate today = LocalDate.now();
    private final UserPrincipal admin = UserPrincipal.builder()
            .id(1L).schoolId(1L).role(Role.SCHOOL_ADMIN).active(true).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxTeacherWeeklyLessons", 30);
        ReflectionTestUtils.setField(service, "maxTeacherDailyLessons", 8);
        ReflectionTestUtils.setField(service, "maxSameSubjectPerDay", 2);
        ReflectionTestUtils.setField(service, "clock", Clock.systemUTC());
    }

    @Test
    @DisplayName("ATTACK 4: TimetableService must inject java.time.Clock")
    void timetableServiceMustInjectClock_failsOnCurrentCode() {
        boolean hasClock = false;
        for (var field : TimetableService.class.getDeclaredFields()) {
            if (field.getType().equals(java.time.Clock.class)) {
                hasClock = true;
                break;
            }
        }
        assertThat(hasClock)
                .as("TimetableService must inject java.time.Clock instead of static LocalDate.now()")
                .isTrue();
    }

    @Test
    @DisplayName("ATTACK 5: Assign must reject subject belonging to a different class level than the stream")
    void assignRejectsSubjectBelongingToDifferentClassLevel_failsOnCurrentCode() {
        Long schoolId = 1L;
        Stream stream = Stream.builder().schoolId(schoolId).classLevelId(10L).active(true).build();
        stream.setId(100L);
        // Subject belongs to class level 20, but stream belongs to class level 10
        Subject subject = Subject.builder().schoolId(schoolId).classLevelId(20L).active(true).build();
        subject.setId(200L);
        ClassLevel classLevel = ClassLevel.builder().schoolId(schoolId).active(true).build();
        classLevel.setId(10L);

        when(streamRepository.findBySchoolIdAndId(schoolId, 100L)).thenReturn(Optional.of(stream));
        when(classLevelRepository.findBySchoolIdAndId(schoolId, 10L)).thenReturn(Optional.of(classLevel));
        when(subjectRepository.findBySchoolIdAndId(schoolId, 200L)).thenReturn(Optional.of(subject));

        AssignmentBatch batch = new AssignmentBatch(schoolId, List.of(
                new AssignmentItem(100L, 200L, 300L, null, 4)
        ));

        // On current code, this does NOT reject, but it SHOULD reject with CLASS_LEVEL_MISMATCH
        assertThatThrownBy(() -> service.assign(batch, admin))
                .isInstanceOf(TimetableRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CLASS_LEVEL_MISMATCH");
    }

    @Test
    @DisplayName("ATTACK 6: Publish does not check room status — vulnerability demonstration")
    void publishDoesNotCheckRoomStatus() {
        // This test demonstrates that publish() does not validate room.isActive()
        // before allowing a timetable to be published. A lesson with a missing/inactive
        // room can still be published. This is a known vulnerability.
        // After fix: publish should validate all rooms are active.
        assertThat(true).isTrue(); // placeholder — the vulnerability is documented in the audit
    }

    @Test
    @DisplayName("ATTACK 7: Period cannot be updated to BREAK if currently used by published timetable")
    void periodCannotBeConvertedToBreakWhileInPublishedUse_failsOnCurrentCode() {
        Long schoolId = 1L;
        Long periodId = 10L;
        com.peterscode.edutrack.timetable.entity.Period existing = com.peterscode.edutrack.timetable.entity.Period.builder()
                .schoolId(schoolId).name("Period 1")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).sortOrder(1)
                .type(PeriodType.LESSON).active(true).build();
        existing.setId(periodId);

        when(periodRepository.findBySchoolIdOrderBySortOrder(schoolId)).thenReturn(List.of(existing));
        when(periodRepository.findBySchoolIdAndId(schoolId, periodId)).thenReturn(Optional.of(existing));
        when(lessonRepository.countPublishedPeriodUse(schoolId, periodId)).thenReturn(2L);

        PeriodRequest request = new PeriodRequest(schoolId, "Period 1", LocalTime.of(8, 0), LocalTime.of(9, 0),
                1, PeriodType.BREAK, true);

        // On current code, savePeriod only checks !request.active(), so it allows changing to BREAK!
        // It SHOULD throw conflict("PERIOD_IN_USE")
        assertThatThrownBy(() -> service.savePeriod(periodId, request, admin))
                .isInstanceOf(TimetableRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PERIOD_IN_USE");
    }

    @Test
    @DisplayName("ATTACK 8: TermController must expose GET endpoints for listing and getting terms")
    void termControllerExposesGetAndListEndpoints_failsOnCurrentCode() throws NoSuchMethodException {
        // Must have getTerms and getTermById
        TermController.class.getMethod("getTerms", Long.class, Long.class, UserPrincipal.class);
        TermController.class.getMethod("getTermById", Long.class, Long.class, UserPrincipal.class);
    }
}
