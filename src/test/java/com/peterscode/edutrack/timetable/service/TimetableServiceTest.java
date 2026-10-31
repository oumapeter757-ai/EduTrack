package com.peterscode.edutrack.timetable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.subject.entity.Subject;
import com.peterscode.edutrack.subject.repository.SubjectRepository;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.AssignmentBatch;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.AssignmentItem;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.LessonBatch;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.LessonItem;
import com.peterscode.edutrack.timetable.entity.Period;
import com.peterscode.edutrack.timetable.entity.StreamSubject;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.entity.Timetable;
import com.peterscode.edutrack.timetable.exception.TimetableRuleException;
import com.peterscode.edutrack.timetable.repository.*;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {
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
    @InjectMocks private TimetableService service;

    private final UserPrincipal principal = UserPrincipal.builder()
            .id(100L).schoolId(1L).role(Role.SCHOOL_ADMIN).active(true).build();
    private final AcademicYear year = AcademicYear.builder().active(true).build();
    private final ClassLevel classLevel = ClassLevel.builder().active(true).build();
    private final Subject subject = Subject.builder().schoolId(1L).active(true).build();
    private final Staff staff = Staff.builder().schoolId(1L).userId(200L).active(true)
            .status(com.peterscode.edutrack.staff.enums.StaffStatus.ACTIVE).build();
    private final User teacher = User.builder().schoolId(1L).active(true).role(Role.TEACHER).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxTeacherWeeklyLessons", 30);
        ReflectionTestUtils.setField(service, "maxTeacherDailyLessons", 8);
        ReflectionTestUtils.setField(service, "maxSameSubjectPerDay", 2);

        year.setId(10L);
        classLevel.setId(20L);
        subject.setId(30L);
        staff.setId(40L);
        teacher.setId(200L);
        when(classLevelRepository.findBySchoolIdAndId(1L, 20L)).thenReturn(Optional.of(classLevel));
        when(subjectRepository.findBySchoolIdAndId(1L, 30L)).thenReturn(Optional.of(subject));
        when(staffRepository.findBySchoolIdAndId(1L, 40L)).thenReturn(Optional.of(staff));
        when(userRepository.findBySchoolIdAndId(1L, 200L)).thenReturn(Optional.of(teacher));
    }

    @Test
    void assign_rejectsBatchThatExceedsTeacherWeeklyLimit() {
        when(academicYearRepository.findBySchoolIdAndId(1L, 10L)).thenReturn(Optional.of(year));
        Stream firstStream = Stream.builder().schoolId(1L).classLevelId(20L).active(true).build();
        firstStream.setId(50L);
        Stream secondStream = Stream.builder().schoolId(1L).classLevelId(20L).active(true).build();
        secondStream.setId(51L);
        when(streamRepository.findBySchoolIdAndId(1L, 50L)).thenReturn(Optional.of(firstStream));
        when(streamRepository.findBySchoolIdAndId(1L, 51L)).thenReturn(Optional.of(secondStream));
        when(assignmentRepository.findBySchoolIdAndStreamIdAndAcademicYearId(1L, 50L, 10L)).thenReturn(List.of());
        when(assignmentRepository.findBySchoolIdAndStreamIdAndAcademicYearId(1L, 51L, 10L)).thenReturn(List.of());
        when(assignmentRepository.sumWeeklyLessons(1L, 40L, 10L, null)).thenReturn(0L);

        AssignmentBatch batch = new AssignmentBatch(1L, List.of(
                new AssignmentItem(50L, 30L, 10L, 40L, 20),
                new AssignmentItem(51L, 30L, 10L, 40L, 20)));

        assertThatThrownBy(() -> service.assign(batch, principal))
                .isInstanceOf(TimetableRuleException.class)
                .hasMessage("Teacher assignment exceeds the weekly lesson limit");
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    void createLessons_rejectsBatchThatExceedsAssignmentWeeklyLimit() {
        Timetable timetable = Timetable.builder().schoolId(1L).termId(60L)
                .status(Timetable.TimetableStatus.DRAFT).build();
        timetable.setId(70L);
        Term term = Term.builder().schoolId(1L).academicYearId(10L)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(30)).build();
        term.setId(60L);
        School school = School.builder().timezone("UTC").build();
        Stream stream = Stream.builder().schoolId(1L).classLevelId(20L).active(true).build();
        stream.setId(50L);
        StreamSubject assignment = StreamSubject.builder().schoolId(1L).streamId(50L).subjectId(30L)
                .academicYearId(10L).teacherId(40L).lessonsPerWeek(1).build();
        assignment.setId(80L);
        Period period = Period.builder().schoolId(1L).type(Period.PeriodType.LESSON).active(true).build();
        period.setId(90L);

        when(timetableRepository.findBySchoolIdAndId(1L, 70L)).thenReturn(Optional.of(timetable));
        when(termRepository.findBySchoolIdAndId(1L, 60L)).thenReturn(Optional.of(term));
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(assignmentRepository.findBySchoolIdAndId(1L, 80L)).thenReturn(Optional.of(assignment));
        when(streamRepository.findBySchoolIdAndId(1L, 50L)).thenReturn(Optional.of(stream));
        when(periodRepository.findBySchoolIdAndId(1L, 90L)).thenReturn(Optional.of(period));
        when(lessonRepository.countBySchoolIdAndTimetableIdAndStreamSubjectId(1L, 70L, 80L)).thenReturn(0L);
        when(lessonRepository.countSlotConflicts(1L, 70L, 1, 90L, 50L, 40L, null, null)).thenReturn(0L);
        when(lessonRepository.countSameSubjectForDay(1L, 70L, 50L, 80L, 1, null)).thenReturn(0L);
        when(lessonRepository.countTeacherForDay(1L, 70L, 40L, 1, null)).thenReturn(0L);

        LessonBatch batch = new LessonBatch(1L, 70L, List.of(
                new LessonItem(80L, 90L, 1, null),
                new LessonItem(80L, 90L, 2, null)));

        assertThatThrownBy(() -> service.createLessons(batch, principal))
                .isInstanceOf(TimetableRuleException.class)
                .hasMessageContaining("WEEKLY_SUBJECT_LIMIT");
        verify(lessonRepository, never()).saveAllAndFlush(any());
    }
}
