package com.peterscode.edutrack.timetable.service;

import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import com.peterscode.edutrack.academicyear.repository.AcademicYearRepository;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.enums.StaffStatus;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.subject.entity.Subject;
import com.peterscode.edutrack.subject.repository.SubjectRepository;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.*;
import com.peterscode.edutrack.timetable.dto.TimetableViews.*;
import com.peterscode.edutrack.timetable.entity.*;
import com.peterscode.edutrack.timetable.entity.Period.PeriodType;
import com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus;
import com.peterscode.edutrack.timetable.event.TimetablePublishedEvent;
import com.peterscode.edutrack.timetable.exception.TimetableRuleException;
import com.peterscode.edutrack.timetable.repository.*;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import com.peterscode.edutrack.attendance.repository.StudentAttendanceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimetableService {
    private final SchoolRepository schoolRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ClassLevelRepository classLevelRepository;
    private final StreamRepository streamRepository;
    private final SubjectRepository subjectRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TermRepository termRepository;
    private final PeriodRepository periodRepository;
    private final RoomRepository roomRepository;
    private final ClassLevelSubjectRepository curriculumRepository;
    private final StreamSubjectRepository assignmentRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableLessonRepository lessonRepository;
    private final TimetableAuditRepository auditRepository;
    private final ApplicationEventPublisher events;
    private final StudentAttendanceRepository attendanceRepository;
    private final Clock clock;

    @Value("${edutrack.timetable.max-teacher-weekly-lessons:30}")
    private int maxTeacherWeeklyLessons;
    @Value("${edutrack.timetable.max-teacher-daily-lessons:8}")
    private int maxTeacherDailyLessons;
    @Value("${edutrack.timetable.max-same-subject-per-day:2}")
    private int maxSameSubjectPerDay;

    @jakarta.annotation.PostConstruct
    void validateLimits() {
        if (maxTeacherWeeklyLessons < 1 || maxTeacherDailyLessons < 1 || maxSameSubjectPerDay < 1) {
            throw new IllegalStateException("Timetable lesson limits must be positive");
        }
    }

    @Transactional
    public TermView createTerm(TermRequest request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        AcademicYear year = academicYearRepository.findBySchoolIdAndId(schoolId, request.academicYearId())
                .orElseThrow(() -> notFound("Academic year not found"));
        if (request.endDate().isBefore(request.startDate())
                || request.startDate().isBefore(year.getStartDate())
                || request.endDate().isAfter(year.getEndDate())) {
            throw bad("INVALID_TERM_DATES", "Term dates must fit within the academic year");
        }
        if (termRepository.existsBySchoolIdAndAcademicYearIdAndName(schoolId, year.getId(), request.name().trim())) {
            throw conflict("TERM_EXISTS", "A term with this name already exists in the academic year");
        }
        Term saved = termRepository.save(Term.builder().schoolId(schoolId).academicYearId(year.getId())
                .name(request.name().trim()).startDate(request.startDate()).endDate(request.endDate()).active(true).build());
        return termView(saved);
    }

    @Transactional(readOnly = true)
    public List<TermView> terms(Long academicYearId, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        return (academicYearId == null
                ? termRepository.findBySchoolIdOrderByStartDateAsc(schoolId)
                : termRepository.findBySchoolIdAndAcademicYearIdOrderByStartDateAsc(schoolId, academicYearId))
                .stream().map(this::termView).toList();
    }

    @Transactional(readOnly = true)
    public TermView term(Long id, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        return termRepository.findBySchoolIdAndId(schoolId, id)
                .map(this::termView)
                .orElseThrow(() -> notFound("Term not found"));
    }

    @Transactional
    public PeriodView savePeriod(Long id, PeriodRequest request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        if (!request.endTime().isAfter(request.startTime())) throw bad("INVALID_PERIOD_TIME", "Period end time must be after start time");
        for (Period other : periodRepository.findBySchoolIdOrderBySortOrder(schoolId)) {
            if (id != null && id.equals(other.getId())) continue;
            boolean overlaps = request.startTime().isBefore(other.getEndTime()) && request.endTime().isAfter(other.getStartTime());
            if (other.isActive() && overlaps) throw conflict("PERIOD_OVERLAP", "Period overlaps with " + other.getName());
        }
        Period period = id == null ? new Period() : periodRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> notFound("Period not found"));
        if (!request.active() && period.getId() != null && lessonRepository.countPublishedPeriodUse(schoolId, id) > 0) {
            throw conflict("PERIOD_IN_USE", "A period used by a published timetable cannot be deactivated");
        }
        if (request.type() == PeriodType.BREAK && period.getId() != null && lessonRepository.countPublishedPeriodUse(schoolId, id) > 0) {
            throw conflict("PERIOD_IN_USE", "A period used by a published timetable cannot be changed to a break");
        }
        period.setSchoolId(schoolId);
        period.setName(request.name().trim());
        period.setStartTime(request.startTime());
        period.setEndTime(request.endTime());
        period.setSortOrder(request.sortOrder());
        period.setType(request.type());
        period.setActive(request.active());
        return periodView(periodRepository.save(period));
    }

    @Transactional(readOnly = true)
    public List<PeriodView> periods(Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        return periodRepository.findBySchoolIdOrderBySortOrder(schoolId).stream().map(this::periodView).toList();
    }

    @Transactional
    public RoomView saveRoom(Long id, RoomRequest request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        Room room = id == null ? new Room() : roomRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> notFound("Room not found"));
        if (!request.active() && room.getId() != null && lessonRepository.countPublishedRoomUse(schoolId, id) > 0) {
            throw conflict("ROOM_IN_USE", "A room used by a published timetable cannot be deactivated");
        }
        room.setSchoolId(schoolId);
        room.setName(request.name().trim());
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.setActive(request.active());
        return roomView(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomView> rooms(Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        return roomRepository.findBySchoolIdOrderByName(schoolId).stream().map(this::roomView).toList();
    }

    @Transactional
    public List<CurriculumView> saveCurriculum(CurriculumRequest request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        ClassLevel classLevel = classLevelRepository.findBySchoolIdAndId(schoolId, request.classLevelId())
                .orElseThrow(() -> notFound("Class level not found"));
        Set<Long> seen = new HashSet<>();
        List<ClassLevelSubject> toSave = new ArrayList<>();
        for (CurriculumItem item : request.subjects()) {
            if (!seen.add(item.subjectId())) throw bad("DUPLICATE_CURRICULUM_SUBJECT", "Subject appears more than once in the curriculum");
            requireSubject(schoolId, item.subjectId());
            ClassLevelSubject row = curriculumRepository.findBySchoolIdAndClassLevelIdAndSubjectId(schoolId, classLevel.getId(), item.subjectId())
                    .orElseGet(ClassLevelSubject::new);
            row.setSchoolId(schoolId);
            row.setClassLevelId(classLevel.getId());
            row.setSubjectId(item.subjectId());
            row.setDefaultLessonsPerWeek(item.lessonsPerWeek());
            row.setCompulsory(item.compulsory());
            toSave.add(row);
        }
        return curriculumRepository.saveAll(toSave).stream().map(this::curriculumView).toList();
    }

    @Transactional(readOnly = true)
    public List<CurriculumView> curriculum(Long classLevelId, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        requireClassLevel(schoolId, classLevelId);
        return curriculumRepository.findBySchoolIdAndClassLevelId(schoolId, classLevelId).stream().map(this::curriculumView).toList();
    }

    @Transactional
    public List<AssignmentView> applyCurriculum(Long classLevelId, ApplyCurriculumRequest request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        requireClassLevel(schoolId, classLevelId);
        requireAcademicYear(schoolId, request.academicYearId());
        List<ClassLevelSubject> template = curriculumRepository.findBySchoolIdAndClassLevelId(schoolId, classLevelId);
        if (template.isEmpty()) throw notFound("Curriculum template not found");
        List<Stream> streams = request.streamIds().stream().distinct().map(id -> requireStream(schoolId, id))
                .peek(s -> { if (!classLevelId.equals(s.getClassLevelId())) throw notFound("Stream not found"); }).toList();
        List<StreamSubject> rows = new ArrayList<>();
        for (Stream stream : streams) {
            for (ClassLevelSubject item : template) {
                StreamSubject row = assignmentRepository.findBySchoolIdAndStreamIdAndAcademicYearId(schoolId, stream.getId(), request.academicYearId())
                        .stream().filter(a -> a.getSubjectId().equals(item.getSubjectId())).findFirst().orElseGet(StreamSubject::new);
                row.setSchoolId(schoolId);
                row.setStreamId(stream.getId());
                row.setSubjectId(item.getSubjectId());
                row.setAcademicYearId(request.academicYearId());
                row.setLessonsPerWeek(item.getDefaultLessonsPerWeek());
                rows.add(row);
            }
        }
        return assignmentRepository.saveAll(rows).stream().map(this::assignmentView).toList();
    }

    @Transactional
    public List<AssignmentView> assign(AssignmentBatch request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        List<StreamSubject> rows = new ArrayList<>();
        Set<String> uniquePairs = new HashSet<>();
        Map<TeacherYear, Long> weeklyLessonDeltas = new HashMap<>();
        for (AssignmentItem item : request.assignments()) {
            String pair = item.streamId() + ":" + item.subjectId() + ":" + item.academicYearId();
            if (!uniquePairs.add(pair)) throw bad("DUPLICATE_ASSIGNMENT", "Assignment batch contains the same stream subject more than once");
            Stream stream = requireStream(schoolId, item.streamId());
            Subject subject = requireSubject(schoolId, item.subjectId());
            if (subject.getClassLevelId() != null && !subject.getClassLevelId().equals(stream.getClassLevelId())) {
                throw bad("CLASS_LEVEL_MISMATCH", "Subject does not belong to the stream's class level");
            }
            requireAcademicYear(schoolId, item.academicYearId());
            StreamSubject assignment = assignmentRepository.findBySchoolIdAndStreamIdAndAcademicYearId(schoolId, stream.getId(), item.academicYearId())
                    .stream().filter(a -> a.getSubjectId().equals(item.subjectId())).findFirst().orElseGet(StreamSubject::new);
            if (assignment.getId() != null && item.lessonsPerWeek() < assignment.getLessonsPerWeek()) {
                List<TimetableLesson> existingLessons = lessonRepository.findBySchoolIdAndStreamSubjectId(schoolId, assignment.getId());
                for (TimetableLesson l : existingLessons) {
                    Timetable tt = requireTimetable(schoolId, l.getTimetableId());
                    if (tt.getStatus() == TimetableStatus.PUBLISHED) {
                        throw conflict("PUBLISHED_ASSIGNMENT", "Cannot reduce weekly lessons while a published timetable uses this assignment");
                    }
                }
            }
            Long previousTeacher = assignment.getId() == null ? null : assignment.getTeacherId();
            if (item.teacherId() != null) validateTeacher(schoolId, item.teacherId());
            Long previousLessons = assignment.getId() == null ? null : assignment.getLessonsPerWeek().longValue();
            if (item.teacherId() != null) {
                TeacherYear teacherYear = new TeacherYear(item.teacherId(), item.academicYearId());
                long projectedLessons = assignmentRepository.sumWeeklyLessons(
                        schoolId, item.teacherId(), item.academicYearId(), assignment.getId())
                        + weeklyLessonDeltas.getOrDefault(teacherYear, 0L)
                        + item.lessonsPerWeek();
                if (projectedLessons > maxTeacherWeeklyLessons) {
                    throw conflict("TEACHER_WEEKLY_LIMIT", "Teacher assignment exceeds the weekly lesson limit");
                }
            }
            if (assignment.getId() != null && !Objects.equals(previousTeacher, item.teacherId())) {
                updateAssignedLessonsTeacher(schoolId, assignment, item.teacherId());
                audit(schoolId, "STREAM_SUBJECT", assignment.getId(), "TEACHER_REASSIGNED", principal.getId(),
                        "teacher=" + previousTeacher, "teacher=" + item.teacherId());
            }
            if (previousTeacher != null) {
                TeacherYear previousTeacherYear = new TeacherYear(previousTeacher, item.academicYearId());
                weeklyLessonDeltas.merge(previousTeacherYear, -previousLessons, Long::sum);
            }
            if (item.teacherId() != null) {
                TeacherYear teacherYear = new TeacherYear(item.teacherId(), item.academicYearId());
                weeklyLessonDeltas.merge(teacherYear, item.lessonsPerWeek().longValue(), Long::sum);
            }
            assignment.setSchoolId(schoolId);
            assignment.setStreamId(stream.getId());
            assignment.setSubjectId(item.subjectId());
            assignment.setAcademicYearId(item.academicYearId());
            assignment.setTeacherId(item.teacherId());
            assignment.setLessonsPerWeek(item.lessonsPerWeek());
            rows.add(assignment);
        }
        List<StreamSubject> saved = assignmentRepository.saveAll(rows);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() == null) {
                StreamSubject assigned = saved.get(i);
                audit(schoolId, "STREAM_SUBJECT", assigned.getId(), "CREATED", principal.getId(), null,
                        "stream=" + assigned.getStreamId() + ",subject=" + assigned.getSubjectId());
            }
        }
        return saved.stream().map(this::assignmentView).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> assignments(Long streamId, Long academicYearId, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        if (streamId != null) requireStream(schoolId, streamId);
        requireAcademicYear(schoolId, academicYearId);
        return (streamId == null ? assignmentRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
                : assignmentRepository.findBySchoolIdAndStreamIdAndAcademicYearId(schoolId, streamId, academicYearId))
                .stream().map(this::assignmentView).toList();
    }

    @Transactional
    public TimetableView createTimetable(TimetableRequest request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        Term term = termRepository.findBySchoolIdAndId(schoolId, request.termId()).orElseThrow(() -> notFound("Term not found"));
        if (timetableRepository.findBySchoolIdAndTermId(schoolId, term.getId()).isPresent()) {
            throw conflict("TIMETABLE_EXISTS", "A timetable already exists for this term");
        }
        Timetable timetable = Timetable.builder().schoolId(schoolId).termId(term.getId()).status(TimetableStatus.DRAFT).version(0L).build();
        return timetableView(timetableRepository.save(timetable));
    }

    @Transactional(readOnly = true)
    public TimetableView timetable(Long id, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        return timetableView(requireTimetable(schoolId, id));
    }

    @Transactional
    public LessonBatchView createLessons(LessonBatch request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireAdmin(principal);
        Timetable timetable = timetableRepository.findLocked(schoolId, request.timetableId())
                .orElseThrow(() -> notFound("Timetable not found"));
        requireWritableTerm(schoolId, timetable);
        if (timetable.getStatus() != TimetableStatus.DRAFT) throw conflict("TIMETABLE_PUBLISHED", "Published timetable cannot be changed");
        List<String> conflicts = new ArrayList<>();
        Set<String> batchSlots = new HashSet<>();
        Map<Long, Integer> batchAssignmentCounts = new HashMap<>();
        Map<AssignmentDay, Integer> batchAssignmentDailyCounts = new HashMap<>();
        Map<TeacherDay, Integer> batchTeacherDailyCounts = new HashMap<>();
        List<TimetableLesson> lessons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Term term = termRepository.findBySchoolIdAndId(schoolId, timetable.getTermId())
                .orElseThrow(() -> notFound("Term not found"));
        for (LessonItem item : request.lessons()) {
            try {
                StreamSubject assignment = requireAssignment(schoolId, item.streamSubjectId());
                if (!Objects.equals(assignment.getAcademicYearId(), term.getAcademicYearId())) {
                    throw bad("ACADEMIC_YEAR_MISMATCH", "Stream subject assignment does not belong to the timetable's academic year");
                }
                Period period = requirePeriod(schoolId, item.periodId());
                Stream stream = requireStream(schoolId, assignment.getStreamId());
                Long teacher = assignment.getTeacherId();
                if (teacher == null) throw bad("TEACHER_NOT_ASSIGNED", "The stream subject has no assigned teacher");
                validateTeacher(schoolId, teacher);
                if (period.getType() == PeriodType.BREAK) throw bad("BREAK_PERIOD", "A lesson cannot be placed in a break period");
                int assignmentBatchCount = batchAssignmentCounts.getOrDefault(assignment.getId(), 0);
                if (countForAssignment(schoolId, timetable, assignment) + assignmentBatchCount >= assignment.getLessonsPerWeek()) {
                    throw conflict("WEEKLY_SUBJECT_LIMIT", "Stream subject has reached its lessons-per-week limit");
                }
                Room room = item.roomId() == null ? null : requireRoom(schoolId, item.roomId());
                Subject subject = requireSubject(schoolId, assignment.getSubjectId());
                validateRequiredRoom(subject, room);
                String slot = item.dayOfWeek() + ":" + item.periodId() + ":" + stream.getId();
                String teacherSlot = item.dayOfWeek() + ":" + item.periodId() + ":T:" + teacher;
                String roomSlot = room == null ? null : item.dayOfWeek() + ":" + item.periodId() + ":R:" + room.getId();
                if (!batchSlots.add(slot)) throw conflict("STREAM_CLASH", "Batch contains a stream clash");
                if (!batchSlots.add(teacherSlot)) throw conflict("TEACHER_CLASH", "Batch contains a teacher clash");
                if (roomSlot != null && !batchSlots.add(roomSlot)) throw conflict("ROOM_CLASH", "Batch contains a room clash");
                long clash = lessonRepository.countSlotConflicts(schoolId, timetable.getId(), item.dayOfWeek(), period.getId(),
                        stream.getId(), teacher, item.roomId(), null);
                if (clash > 0) throw conflict(conflictCode(schoolId, timetable, item, stream.getId(), teacher),
                        "The selected period is already occupied by a conflicting lesson");
                AssignmentDay assignmentDay = new AssignmentDay(assignment.getId(), item.dayOfWeek());
                TeacherDay teacherDay = new TeacherDay(teacher, item.dayOfWeek());
                validateDailyLimits(schoolId, timetable, assignment, stream, teacher, item.dayOfWeek(), null,
                        batchAssignmentDailyCounts.getOrDefault(assignmentDay, 0),
                        batchTeacherDailyCounts.getOrDefault(teacherDay, 0));
                if (room != null && studentRepository.countBySchoolIdAndStreamIdAndActiveTrue(schoolId, stream.getId()) > room.getCapacity()) {
                    warnings.add("Room " + room.getName() + " capacity is below the stream's active student count");
                }
                lessons.add(TimetableLesson.builder().schoolId(schoolId).timetableId(timetable.getId())
                        .streamSubjectId(assignment.getId()).streamId(stream.getId()).teacherId(teacher)
                        .roomId(room == null ? null : room.getId()).periodId(period.getId()).dayOfWeek(item.dayOfWeek()).build());
                batchAssignmentCounts.merge(assignment.getId(), 1, Integer::sum);
                batchAssignmentDailyCounts.merge(assignmentDay, 1, Integer::sum);
                batchTeacherDailyCounts.merge(teacherDay, 1, Integer::sum);
            } catch (TimetableRuleException ex) {
                conflicts.add(ex.getErrorCode() + ": " + ex.getMessage());
            }
        }
        if (!conflicts.isEmpty()) throw conflict("TIMETABLE_CONFLICT", String.join("; ", conflicts));
        List<TimetableLesson> saved = lessonRepository.saveAllAndFlush(lessons);
        saved.forEach(lesson -> audit(schoolId, "LESSON", lesson.getId(), "CREATED", principal.getId(), null,
                lesson.getDayOfWeek() + ":" + lesson.getPeriodId() + ":" + lesson.getStreamId()));
        return new LessonBatchView(saved.stream().map(l -> lessonView(schoolId, l)).toList(), warnings);
    }

    @Transactional
    public LessonView moveLesson(Long lessonId, MoveLessonRequest request, Long explicitSchoolId, UserPrincipal principal) {
        requireAdmin(principal);
        Long schoolId = schoolId(explicitSchoolId, principal);
        TimetableLesson lesson = lessonRepository.findBySchoolIdAndId(schoolId, lessonId).orElseThrow(() -> notFound("Lesson not found"));
        Timetable timetable = timetableRepository.findLocked(schoolId, lesson.getTimetableId())
                .orElseThrow(() -> notFound("Timetable not found"));
        requireWritableTerm(schoolId, timetable);
        if (timetable.getStatus() != TimetableStatus.DRAFT) throw conflict("TIMETABLE_PUBLISHED", "Published timetable cannot be changed");
        String old = lesson.getDayOfWeek() + ":" + lesson.getPeriodId() + ":" + lesson.getRoomId();
        StreamSubject assignment = requireAssignment(schoolId, lesson.getStreamSubjectId());
        Stream stream = requireStream(schoolId, lesson.getStreamId());
        Period period = requirePeriod(schoolId, request.periodId());
        Room room = request.roomId() == null ? null : requireRoom(schoolId, request.roomId());
        if (period.getType() == PeriodType.BREAK) throw bad("BREAK_PERIOD", "A lesson cannot be placed in a break period");
        validateRequiredRoom(requireSubject(schoolId, assignment.getSubjectId()), room);
        if (lessonRepository.countSlotConflicts(schoolId, timetable.getId(), request.dayOfWeek(), period.getId(),
                stream.getId(), lesson.getTeacherId(), request.roomId(), lessonId) > 0) {
            throw conflict("TIMETABLE_CONFLICT", "The selected period is already occupied by a conflicting lesson");
        }
        validateDailyLimits(schoolId, timetable, assignment, stream, lesson.getTeacherId(), request.dayOfWeek(), lessonId);
        lesson.setPeriodId(period.getId());
        lesson.setDayOfWeek(request.dayOfWeek());
        lesson.setRoomId(request.roomId());
        TimetableLesson saved = lessonRepository.saveAndFlush(lesson);
        audit(schoolId, "LESSON", lessonId, "MOVED", principal.getId(), old,
                saved.getDayOfWeek() + ":" + saved.getPeriodId() + ":" + saved.getRoomId());
        return lessonView(schoolId, saved);
    }

    @Transactional
    public void deleteLesson(Long lessonId, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        requireAdmin(principal);
        TimetableLesson lesson = lessonRepository.findBySchoolIdAndId(schoolId, lessonId).orElseThrow(() -> notFound("Lesson not found"));
        Timetable timetable = timetableRepository.findLocked(schoolId, lesson.getTimetableId())
                .orElseThrow(() -> notFound("Timetable not found"));
        requireWritableTerm(schoolId, timetable);
        if (timetable.getStatus() != TimetableStatus.DRAFT) throw conflict("TIMETABLE_PUBLISHED", "Published timetable cannot be changed");
        audit(schoolId, "LESSON", lessonId, "DELETED", principal.getId(),
                lesson.getDayOfWeek() + ":" + lesson.getPeriodId() + ":" + lesson.getStreamId(), null);
        lessonRepository.delete(lesson);
    }

    @Transactional(readOnly = true)
    public List<LessonView> streamLessons(Long timetableId, Long streamId, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        requireAdmin(principal);
        requireStream(schoolId, streamId);
        Timetable timetable = requireTimetable(schoolId, timetableId);
        return lessonRepository.findBySchoolIdAndTimetableIdOrderByDayOfWeekAscPeriodIdAsc(schoolId, timetableId).stream()
                .filter(l -> streamId.equals(l.getStreamId())).map(l -> lessonView(schoolId, l)).toList();
    }

    @Transactional
    public PublishView publish(Long timetableId, Long explicitSchoolId, boolean acknowledgeIncomplete, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        requireAdmin(principal);
        Timetable timetable = timetableRepository.findLocked(schoolId, timetableId).orElseThrow(() -> notFound("Timetable not found"));
        requireWritableTerm(schoolId, timetable);
        List<TimetableLesson> lessons = lessonRepository.findBySchoolIdAndTimetableIdOrderByDayOfWeekAscPeriodIdAsc(schoolId, timetableId);
        validatePublishClashes(schoolId, timetable, lessons);
        CompletenessView completeness = completeness(schoolId, timetable);
        if (!completeness.complete() && !acknowledgeIncomplete) {
            throw conflict("INCOMPLETE_TIMETABLE", "Acknowledge incomplete timetable coverage before publishing");
        }
        timetable.setStatus(TimetableStatus.PUBLISHED);
        timetable.setPublishedAt(LocalDateTime.now(schoolZone(schoolId)));
        timetable.setPublishedBy(principal.getId());
        Timetable saved = timetableRepository.saveAndFlush(timetable);
        audit(schoolId, "TIMETABLE", saved.getId(), "PUBLISHED", principal.getId(), "DRAFT", "PUBLISHED");
        events.publishEvent(new TimetablePublishedEvent(saved.getId(), schoolId, saved.getTermId()));
        return new PublishView(timetableView(saved), completeness);
    }

    @Transactional
    public TimetableView unpublish(Long timetableId, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        requireAdmin(principal);
        Timetable timetable = timetableRepository.findLocked(schoolId, timetableId).orElseThrow(() -> notFound("Timetable not found"));
        requireWritableTerm(schoolId, timetable);
        timetable.setStatus(TimetableStatus.DRAFT);
        timetable.setPublishedAt(null);
        timetable.setPublishedBy(null);
        Timetable saved = timetableRepository.saveAndFlush(timetable);
        audit(schoolId, "TIMETABLE", saved.getId(), "UNPUBLISHED", principal.getId(), "PUBLISHED", "DRAFT");
        return timetableView(saved);
    }

    @Transactional(readOnly = true)
    public CompletenessView completeness(Long timetableId, Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = schoolId(explicitSchoolId, principal);
        requireAdmin(principal);
        return completeness(schoolId, requireTimetable(schoolId, timetableId));
    }

    @Transactional(readOnly = true)
    public List<TeacherLessonView> myTeacherLessons(UserPrincipal principal) {
        Long schoolId = schoolId(null, principal);
        Staff teacher = staffRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                .orElseThrow(() -> notFound("Teacher record not found"));
        LocalDate today = LocalDate.now(schoolZone(schoolId));
        Term activeTerm = termRepository.findActiveTermBySchoolId(schoolId, today)
                .orElse(null);
        if (activeTerm == null) return List.of();
        Timetable activeTimetable = timetableRepository.findBySchoolIdAndTermId(schoolId, activeTerm.getId())
                .filter(t -> t.getStatus() == TimetableStatus.PUBLISHED)
                .orElse(null);
        if (activeTimetable == null) return List.of();
        return lessonRepository.findPublishedTeacherLessons(schoolId, teacher.getId()).stream()
                .filter(l -> l.getTimetableId().equals(activeTimetable.getId()))
                .map(l -> teacherView(schoolId, l)).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentLessonView> myStudentLessons(UserPrincipal principal) {
        Long schoolId = schoolId(null, principal);
        Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                .orElseThrow(() -> notFound("Student record not found"));
        if (student.getStreamId() == null) throw notFound("Student stream not found");
        LocalDate today = LocalDate.now(schoolZone(schoolId));
        Term activeTerm = termRepository.findActiveTermBySchoolId(schoolId, today)
                .orElse(null);
        if (activeTerm == null) return List.of();
        Timetable activeTimetable = timetableRepository.findBySchoolIdAndTermId(schoolId, activeTerm.getId())
                .filter(t -> t.getStatus() == TimetableStatus.PUBLISHED)
                .orElse(null);
        if (activeTimetable == null) return List.of();
        return lessonRepository.findPublishedStreamLessons(schoolId, student.getStreamId()).stream()
                .filter(l -> l.getTimetableId().equals(activeTimetable.getId()))
                .map(l -> studentView(schoolId, l)).toList();
    }

    private CompletenessView completeness(Long schoolId, Timetable timetable) {
        Term term = termRepository.findBySchoolIdAndId(schoolId, timetable.getTermId()).orElseThrow(() -> notFound("Term not found"));
        AcademicYear year = requireAcademicYear(schoolId, term.getAcademicYearId());
        List<MissingLesson> missing = new ArrayList<>();
        for (StreamSubject assignment : assignmentRepository.findBySchoolIdAndAcademicYearId(schoolId, year.getId())) {
            long scheduled = lessonRepository.countBySchoolIdAndTimetableIdAndStreamSubjectId(schoolId, timetable.getId(), assignment.getId());
            if (scheduled < assignment.getLessonsPerWeek()) {
                Stream stream = requireStream(schoolId, assignment.getStreamId());
                Subject subject = requireSubject(schoolId, assignment.getSubjectId());
                missing.add(new MissingLesson(stream.getId(), stream.getName(), subject.getId(), subject.getName(),
                        assignment.getLessonsPerWeek(), scheduled));
            }
        }
        return new CompletenessView(missing.isEmpty(), missing);
    }

    private void validatePublishClashes(Long schoolId, Timetable timetable, List<TimetableLesson> lessons) {
        Set<String> slots = new HashSet<>();
        for (TimetableLesson lesson : lessons) {
            String streamSlot = lesson.getDayOfWeek() + ":" + lesson.getPeriodId() + ":S:" + lesson.getStreamId();
            String teacherSlot = lesson.getDayOfWeek() + ":" + lesson.getPeriodId() + ":T:" + lesson.getTeacherId();
            String roomSlot = lesson.getRoomId() == null ? null : lesson.getDayOfWeek() + ":" + lesson.getPeriodId() + ":R:" + lesson.getRoomId();
            if (!slots.add(streamSlot)) throw conflict("STREAM_CLASH", "Stream clash found while publishing");
            if (!slots.add(teacherSlot)) throw conflict("TEACHER_CLASH", "Teacher clash found while publishing");
            if (roomSlot != null && !slots.add(roomSlot)) throw conflict("ROOM_CLASH", "Room clash found while publishing");
            StreamSubject assignment = requireAssignment(schoolId, lesson.getStreamSubjectId());
            if (!Objects.equals(assignment.getStreamId(), lesson.getStreamId())
                    || !Objects.equals(assignment.getTeacherId(), lesson.getTeacherId())) {
                throw conflict("ASSIGNMENT_MISMATCH", "Lesson no longer matches its stream subject assignment");
            }
            Period period = requirePeriod(schoolId, lesson.getPeriodId());
            if (period.getType() == PeriodType.BREAK) throw conflict("BREAK_PERIOD", "A lesson is assigned to a break period");
            validateTeacher(schoolId, lesson.getTeacherId());
        }
    }

    private void validateDailyLimits(Long schoolId, Timetable timetable, StreamSubject assignment, Stream stream,
            Long teacherId, Integer day, Long exceptLessonId) {
        validateDailyLimits(schoolId, timetable, assignment, stream, teacherId, day, exceptLessonId, 0, 0);
    }

    private void validateDailyLimits(Long schoolId, Timetable timetable, StreamSubject assignment, Stream stream,
            Long teacherId, Integer day, Long exceptLessonId, int batchSubjectCount, int batchTeacherCount) {
        if (lessonRepository.countSameSubjectForDay(schoolId, timetable.getId(), stream.getId(), assignment.getId(), day, exceptLessonId)
                + batchSubjectCount >= maxSameSubjectPerDay) {
            throw conflict("SAME_SUBJECT_DAILY_LIMIT", "Stream subject exceeds the daily lesson limit");
        }
        if (lessonRepository.countTeacherForDay(schoolId, timetable.getId(), teacherId, day, exceptLessonId)
                + batchTeacherCount >= maxTeacherDailyLessons) {
            throw conflict("TEACHER_DAILY_LIMIT", "Teacher exceeds the daily lesson limit");
        }
    }

    private void updateAssignedLessonsTeacher(Long schoolId, StreamSubject assignment, Long newTeacherId) {
        if (newTeacherId == null) {
            List<TimetableLesson> oldLessons = lessonRepository.findBySchoolIdAndStreamSubjectId(schoolId, assignment.getId());
            if (!oldLessons.isEmpty()) throw conflict("LESSONS_EXIST", "Cannot unassign a teacher while lessons exist");
        }
        List<TimetableLesson> affected = lessonRepository.findBySchoolIdAndStreamSubjectId(schoolId, assignment.getId());
        for (TimetableLesson lesson : affected) {
            Timetable timetable = requireTimetable(schoolId, lesson.getTimetableId());
            if (timetable.getStatus() == TimetableStatus.PUBLISHED) {
                throw conflict("PUBLISHED_ASSIGNMENT", "Teacher cannot be reassigned while a published timetable uses this assignment");
            }
            if (newTeacherId != null && lessonRepository.countSlotConflicts(schoolId, timetable.getId(), lesson.getDayOfWeek(),
                    lesson.getPeriodId(), lesson.getStreamId(), newTeacherId, lesson.getRoomId(), lesson.getId()) > 0) {
                throw conflict("TEACHER_CLASH", "Reassigned teacher has a clash in the draft timetable");
            }
        }
        affected.forEach(lesson -> lesson.setTeacherId(newTeacherId));
        lessonRepository.saveAll(affected);
    }

    private String conflictCode(Long schoolId, Timetable timetable, LessonItem item, Long streamId, Long teacherId) {
        List<TimetableLesson> sameSlot = lessonRepository.findBySchoolIdAndTimetableIdOrderByDayOfWeekAscPeriodIdAsc(schoolId, timetable.getId());
        for (TimetableLesson current : sameSlot) {
            if (current.getDayOfWeek().equals(item.dayOfWeek()) && current.getPeriodId().equals(item.periodId())) {
                if (current.getStreamId().equals(streamId)) return "STREAM_CLASH";
                if (current.getTeacherId().equals(teacherId)) return "TEACHER_CLASH";
                if (item.roomId() != null && item.roomId().equals(current.getRoomId())) return "ROOM_CLASH";
            }
        }
        return "TIMETABLE_CONFLICT";
    }

    private long countForAssignment(Long schoolId, Timetable timetable, StreamSubject assignment) {
        return lessonRepository.countBySchoolIdAndTimetableIdAndStreamSubjectId(schoolId, timetable.getId(), assignment.getId());
    }

    private void requireWritableTerm(Long schoolId, Timetable timetable) {
        Term term = termRepository.findBySchoolIdAndId(schoolId, timetable.getTermId()).orElseThrow(() -> notFound("Term not found"));
        if (LocalDate.now(schoolZone(schoolId)).isAfter(term.getEndDate())) {
            throw conflict("TERM_OVER", "The term has ended; timetable changes are not allowed");
        }
    }

    private void validateRequiredRoom(Subject subject, Room room) {
        if (subject.getRequiredRoomType() != null && !subject.getRequiredRoomType().isBlank()) {
            if (room == null) throw bad("ROOM_REQUIRED", "This subject requires a " + subject.getRequiredRoomType() + " room");
            if (!room.getRoomType().name().equalsIgnoreCase(subject.getRequiredRoomType())) {
                throw bad("ROOM_TYPE_MISMATCH", "Room must be of type " + subject.getRequiredRoomType());
            }
        }
    }

    private void validateTeacher(Long schoolId, Long teacherId) {
        Staff staff = staffRepository.findBySchoolIdAndId(schoolId, teacherId).orElseThrow(() -> notFound("Teacher not found"));
        if (!staff.isActive() || staff.getStatus() != StaffStatus.ACTIVE || staff.getUserId() == null) {
            throw bad("TEACHER_INACTIVE", "Teacher must be active");
        }
        User user = userRepository.findBySchoolIdAndId(schoolId, staff.getUserId())
                .orElseThrow(() -> notFound("Teacher not found"));
        if (!user.isActive() || user.getRole() != Role.TEACHER) throw bad("TEACHER_INACTIVE", "Staff record is not an active teacher");
    }

    private Subject requireSubject(Long schoolId, Long id) {
        return subjectRepository.findBySchoolIdAndId(schoolId, id).filter(Subject::isActive)
                .orElseThrow(() -> notFound("Subject not found"));
    }
    private AcademicYear requireAcademicYear(Long schoolId, Long id) {
        return academicYearRepository.findBySchoolIdAndId(schoolId, id).filter(AcademicYear::isActive)
                .orElseThrow(() -> notFound("Academic year not found"));
    }
    private ClassLevel requireClassLevel(Long schoolId, Long id) {
        return classLevelRepository.findBySchoolIdAndId(schoolId, id).filter(ClassLevel::isActive)
                .orElseThrow(() -> notFound("Class level not found"));
    }
    private Stream requireStream(Long schoolId, Long id) {
        Stream stream = streamRepository.findBySchoolIdAndId(schoolId, id).filter(Stream::isActive)
                .orElseThrow(() -> notFound("Stream not found"));
        requireClassLevel(schoolId, stream.getClassLevelId());
        return stream;
    }
    private StreamSubject requireAssignment(Long schoolId, Long id) {
        return assignmentRepository.findBySchoolIdAndId(schoolId, id).orElseThrow(() -> notFound("Stream subject not found"));
    }
    private Period requirePeriod(Long schoolId, Long id) {
        return periodRepository.findBySchoolIdAndId(schoolId, id).filter(Period::isActive).orElseThrow(() -> notFound("Period not found"));
    }
    private Room requireRoom(Long schoolId, Long id) {
        return roomRepository.findBySchoolIdAndId(schoolId, id).filter(Room::isActive).orElseThrow(() -> notFound("Room not found"));
    }
    private Timetable requireTimetable(Long schoolId, Long id) {
        return timetableRepository.findBySchoolIdAndId(schoolId, id).orElseThrow(() -> notFound("Timetable not found"));
    }

    private Long schoolId(Long explicitSchoolId, UserPrincipal principal) {
        if (principal == null) throw new TimetableRuleException("SCHOOL_REQUIRED", HttpStatus.FORBIDDEN, "School context is required");
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId == null) throw bad("SCHOOL_REQUIRED", "SUPER_ADMIN must specify schoolId");
            if (!schoolRepository.existsById(explicitSchoolId)) throw notFound("School not found");
            return explicitSchoolId;
        }
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) throw new TimetableRuleException("SCHOOL_REQUIRED", HttpStatus.FORBIDDEN, "School context is required");
        if (explicitSchoolId != null && !explicitSchoolId.equals(schoolId)) throw forbidden("ACCESS_DENIED", "Access denied");
        return schoolId;
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.getRole() != Role.SUPER_ADMIN && principal.getRole() != Role.SCHOOL_ADMIN) {
            throw new TimetableRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Administrative access is required");
        }
    }
    private ZoneId schoolZone(Long schoolId) {
        School school = schoolRepository.findById(schoolId).orElseThrow(() -> notFound("School not found"));
        try { return ZoneId.of(school.getTimezone()); }
        catch (RuntimeException ex) { return ZoneId.of("UTC"); }
    }
    private void audit(Long schoolId, String type, Long entityId, String action, Long actor,
            String oldValue, String newValue) {
        auditRepository.save(TimetableAudit.builder().schoolId(schoolId).entityType(type).entityId(entityId)
                .action(action).actorId(actor).oldValue(oldValue).newValue(newValue).occurredAt(LocalDateTime.now(clock)).build());
    }
    private LessonView lessonView(Long schoolId, TimetableLesson lesson) {
        StreamSubject assignment = requireAssignment(schoolId, lesson.getStreamSubjectId());
        Subject subject = requireSubject(schoolId, assignment.getSubjectId());
        Staff teacher = staffRepository.findBySchoolIdAndId(schoolId, lesson.getTeacherId()).orElseThrow(() -> notFound("Teacher not found"));
        Period period = requirePeriod(schoolId, lesson.getPeriodId());
        Room room = lesson.getRoomId() == null ? null : roomRepository.findBySchoolIdAndId(schoolId, lesson.getRoomId()).orElseThrow(() -> notFound("Room not found"));
        return new LessonView(lesson.getId(), lesson.getStreamSubjectId(), lesson.getStreamId(), lesson.getTeacherId(),
                lesson.getRoomId(), lesson.getPeriodId(), lesson.getDayOfWeek(), subject.getName(), teacher.getFullName(),
                room == null ? null : room.getName(), period.getStartTime(), period.getEndTime());
    }
    private TeacherLessonView teacherView(Long schoolId, TimetableLesson lesson) {
        LessonView view = lessonView(schoolId, lesson);
        Stream stream = requireStream(schoolId, lesson.getStreamId());
        return new TeacherLessonView(view.subjectName(), stream.getName(), view.roomName(), view.startTime(), view.endTime(), view.dayOfWeek());
    }
    private StudentLessonView studentView(Long schoolId, TimetableLesson lesson) {
        LessonView view = lessonView(schoolId, lesson);
        return new StudentLessonView(view.subjectName(), view.teacherDisplayName(), view.roomName(), view.startTime(), view.endTime(), view.dayOfWeek());
    }
    private TermView termView(Term e) {
        return new TermView(e.getId(), e.getAcademicYearId(), e.getName(), e.getStartDate(), e.getEndDate()); }
    private PeriodView periodView(Period e) { return new PeriodView(e.getId(), e.getName(), e.getStartTime(), e.getEndTime(), e.getSortOrder(), e.getType(), e.isActive()); }
    private RoomView roomView(Room e) { return new RoomView(e.getId(), e.getName(), e.getRoomType(), e.getCapacity(), e.isActive()); }
    private CurriculumView curriculumView(ClassLevelSubject e) { return new CurriculumView(e.getId(), e.getClassLevelId(), e.getSubjectId(), e.getDefaultLessonsPerWeek(), e.isCompulsory()); }
    private AssignmentView assignmentView(StreamSubject e) { return new AssignmentView(e.getId(), e.getStreamId(), e.getSubjectId(), e.getAcademicYearId(), e.getTeacherId(), e.getLessonsPerWeek()); }
    private TimetableView timetableView(Timetable e) { return new TimetableView(e.getId(), e.getTermId(), e.getStatus(), e.getPublishedAt(), e.getVersion()); }
    private TimetableRuleException notFound(String message) { return new TimetableRuleException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message); }
    private TimetableRuleException bad(String code, String message) { return new TimetableRuleException(code, HttpStatus.BAD_REQUEST, message); }
    private TimetableRuleException conflict(String code, String message) { return new TimetableRuleException(code, HttpStatus.CONFLICT, message); }
    private TimetableRuleException forbidden(String code, String message) { return new TimetableRuleException(code, HttpStatus.FORBIDDEN, message); }

    private record AssignmentDay(Long assignmentId, Integer day) {}
    private record TeacherDay(Long teacherId, Integer day) {}
    private record TeacherYear(Long teacherId, Long academicYearId) {}
}
