package com.peterscode.edutrack.attendance.service;

import com.peterscode.edutrack.attendance.dto.AttendanceRequests.BatchRequest;
import com.peterscode.edutrack.attendance.dto.AttendanceRequests.StatusException;
import com.peterscode.edutrack.attendance.dto.AttendanceRequests.StreamQuery;
import com.peterscode.edutrack.attendance.dto.AttendanceViews.BatchResponse;
import com.peterscode.edutrack.attendance.dto.AttendanceViews.StudentAttendanceView;
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
import com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus;
import com.peterscode.edutrack.timetable.entity.TimetableLesson;
import com.peterscode.edutrack.timetable.repository.TermRepository;
import com.peterscode.edutrack.timetable.repository.TimetableLessonRepository;
import com.peterscode.edutrack.timetable.repository.TimetableRepository;
import com.peterscode.edutrack.user.entity.Role;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final SchoolRepository schoolRepository;
    private final StreamRepository streamRepository;
    private final StudentRepository studentRepository;
    private final StudentAttendanceRepository attendanceRepository;
    private final TermRepository termRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableLessonRepository lessonRepository;
    private final StaffRepository staffRepository;
    private final StreamSubjectRepository streamSubjectRepository;
    private final ModuleAccessService moduleAccessService;
    private final Clock clock;

    @Transactional
    public BatchResponse recordBatch(BatchRequest request, UserPrincipal principal) {
        Long schoolId = schoolId(request.schoolId(), principal);
        requireRecorder(principal);
        if (request.attendanceType() == AttendanceType.PERIOD) {
            moduleAccessService.assertModuleEnabled(schoolId, ServiceModule.TIMETABLE);
        }

        Stream stream = streamRepository.findLockedBySchoolIdAndId(schoolId, request.streamId())
                .filter(Stream::isActive)
                .orElseThrow(() -> notFound("Stream not found"));
        Term term = requireTerm(schoolId, request.termId(), request.attendanceDate());
        validateEditableDate(schoolId, request.attendanceDate(), principal);
        TimetableLesson lesson = validateScope(schoolId, stream, term, request.attendanceDate(),
                request.attendanceType(), request.timetableLessonId(), principal, true);

        List<Student> roster = studentRepository
                .findBySchoolIdAndStreamIdAndActiveTrueOrderByFirstNameAscLastNameAsc(schoolId, stream.getId());
        if (roster.isEmpty()) {
            throw bad("EMPTY_ROSTER", "Attendance cannot be recorded for a stream with no active students");
        }

        Map<Long, Student> rosterById = roster.stream().collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<Long, StatusException> exceptions = new HashMap<>();
        for (StatusException exception : request.exceptions() == null ? List.<StatusException>of() : request.exceptions()) {
            if (exceptions.putIfAbsent(exception.studentId(), exception) != null) {
                throw bad("DUPLICATE_STUDENT", "A student appears more than once in the attendance batch");
            }
            if (!rosterById.containsKey(exception.studentId())) {
                throw bad("STUDENT_NOT_IN_STREAM", "Every submitted student must be active in the selected stream");
            }
        }

        Long lessonId = lesson == null ? null : lesson.getId();
        List<StudentAttendance> existing = attendanceRepository
                .findBySchoolIdAndTermIdAndStreamIdAndAttendanceDateAndAttendanceTypeAndTimetableLessonId(
                        schoolId, term.getId(), stream.getId(), request.attendanceDate(), request.attendanceType(), lessonId);
        Map<Long, StudentAttendance> existingByStudent = existing.stream()
                .collect(Collectors.toMap(StudentAttendance::getStudentId, Function.identity()));
        List<StudentAttendance> toSave = new ArrayList<>(roster.size());
        int createdCount = 0;
        int updatedCount = 0;
        for (Student student : roster) {
            StatusException exception = exceptions.get(student.getId());
            StudentAttendance attendance = existingByStudent.get(student.getId());
            if (attendance == null) {
                attendance = StudentAttendance.builder()
                        .schoolId(schoolId)
                        .termId(term.getId())
                        .streamId(stream.getId())
                        .studentId(student.getId())
                        .attendanceDate(request.attendanceDate())
                        .attendanceType(request.attendanceType())
                        .timetableLessonId(lessonId)
                        .build();
                createdCount++;
            } else {
                updatedCount++;
            }
            attendance.setStatus(exception == null ? AttendanceStatus.PRESENT : exception.status());
            attendance.setRemarks(exception == null ? null : cleanRemarks(exception.remarks()));
            attendance.setMarkedBy(principal.getId());
            toSave.add(attendance);
        }

        List<StudentAttendance> saved = attendanceRepository.saveAllAndFlush(toSave);
        return new BatchResponse(roster.size(), createdCount, updatedCount,
                saved.stream().map(row -> view(row, rosterById.get(row.getStudentId()))).toList());
    }

    @Transactional(readOnly = true)
    public List<StudentAttendanceView> streamAttendance(StreamQuery query, UserPrincipal principal) {
        Long schoolId = schoolId(query.schoolId(), principal);
        requireRecorder(principal);
        if (query.attendanceType() == AttendanceType.PERIOD) {
            moduleAccessService.assertModuleEnabled(schoolId, ServiceModule.TIMETABLE);
        }
        Stream stream = streamRepository.findBySchoolIdAndId(schoolId, query.streamId())
                .filter(Stream::isActive)
                .orElseThrow(() -> notFound("Stream not found"));
        Term term = requireTerm(schoolId, query.termId(), query.attendanceDate());
        validateScope(schoolId, stream, term, query.attendanceDate(), query.attendanceType(),
                query.timetableLessonId(), principal, false);

        List<StudentAttendance> rows = attendanceRepository
                .findBySchoolIdAndTermIdAndStreamIdAndAttendanceDateAndAttendanceTypeAndTimetableLessonId(
                        schoolId, term.getId(), stream.getId(), query.attendanceDate(),
                        query.attendanceType(), query.timetableLessonId());
        Map<Long, Student> students = studentRepository.findAllBySchoolIdAndIdIn(
                        schoolId, rows.stream().map(StudentAttendance::getStudentId).toList())
                .stream().collect(Collectors.toMap(Student::getId, Function.identity()));
        return rows.stream().map(row -> view(row, students.get(row.getStudentId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentAttendanceView> myAttendance(UserPrincipal principal) {
        if (principal == null || principal.getRole() != Role.STUDENT) {
            throw forbidden("STUDENT_ACCESS_REQUIRED", "Student access is required");
        }
        Long schoolId = schoolId(null, principal);
        Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                .orElseThrow(() -> notFound("Student record not found"));
        return attendanceRepository.findBySchoolIdAndStudentIdOrderByAttendanceDateDescIdDesc(schoolId, student.getId())
                .stream().map(row -> view(row, student)).toList();
    }

    private Term requireTerm(Long schoolId, Long termId, LocalDate attendanceDate) {
        Term term = termRepository.findBySchoolIdAndIdAndActiveTrue(schoolId, termId)
                .orElseThrow(() -> notFound("Active term not found"));
        if (attendanceDate.isBefore(term.getStartDate()) || attendanceDate.isAfter(term.getEndDate())) {
            throw bad("DATE_OUTSIDE_TERM", "Attendance date must fall within the selected active term");
        }
        return term;
    }

    private TimetableLesson validateScope(Long schoolId, Stream stream, Term term, LocalDate date,
            AttendanceType type, Long timetableLessonId, UserPrincipal principal, boolean lockTimetable) {
        if (type == AttendanceType.DAILY) {
            if (timetableLessonId != null) {
                throw bad("INVALID_ATTENDANCE_SCOPE", "Daily attendance must not include a timetable lesson");
            }
            validateTeacherForDaily(schoolId, term, stream, principal);
            return null;
        }

        if (timetableLessonId == null) {
            throw bad("LESSON_REQUIRED", "Period attendance requires a timetable lesson");
        }
        TimetableLesson lesson = lessonRepository.findBySchoolIdAndId(schoolId, timetableLessonId)
                .orElseThrow(() -> notFound("Timetable lesson not found"));
        if (!stream.getId().equals(lesson.getStreamId())
                || date.getDayOfWeek().getValue() != lesson.getDayOfWeek()) {
            throw bad("LESSON_DATE_MISMATCH", "Timetable lesson must belong to this stream and weekday");
        }
        Timetable timetable = (lockTimetable
                ? timetableRepository.findLocked(schoolId, lesson.getTimetableId())
                : timetableRepository.findBySchoolIdAndId(schoolId, lesson.getTimetableId()))
                .orElseThrow(() -> notFound("Timetable not found"));
        if (timetable.getStatus() != TimetableStatus.PUBLISHED || !term.getId().equals(timetable.getTermId())) {
            throw conflict("PUBLISHED_TIMETABLE_REQUIRED", "Period attendance requires a published timetable for the selected term");
        }
        validateTeacherForLesson(schoolId, lesson, principal);
        return lesson;
    }

    private void validateTeacherForDaily(Long schoolId, Term term, Stream stream, UserPrincipal principal) {
        if (isAdmin(principal)) {
            return;
        }
        Staff teacher = requireTeacher(schoolId, principal);
        boolean hasTimetable = isTimetableEnabled(schoolId);
        if (hasTimetable) {
            if (!lessonRepository.existsPublishedTeachingAssignment(schoolId, term.getId(), stream.getId(), teacher.getId())) {
                throw forbidden("STREAM_NOT_ASSIGNED", "Teacher is not assigned to teach this stream in the active timetable");
            }
        } else {
            if (!streamSubjectRepository.existsBySchoolIdAndTeacherIdAndStreamIdAndAcademicYearId(
                    schoolId, teacher.getId(), stream.getId(), term.getAcademicYearId())) {
                throw forbidden("STREAM_NOT_ASSIGNED", "Teacher is not assigned to teach this stream in the active academic year");
            }
        }
    }

    private boolean isTimetableEnabled(Long schoolId) {
        try {
            moduleAccessService.assertModuleEnabled(schoolId, ServiceModule.TIMETABLE);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void validateTeacherForLesson(Long schoolId, TimetableLesson lesson, UserPrincipal principal) {
        if (isAdmin(principal)) {
            return;
        }
        Staff teacher = requireTeacher(schoolId, principal);
        if (!teacher.getId().equals(lesson.getTeacherId())) {
            throw forbidden("LESSON_NOT_ASSIGNED", "Teacher is not assigned to this timetable lesson");
        }
    }

    private Staff requireTeacher(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() != Role.TEACHER) {
            throw forbidden("ACCESS_DENIED", "Only teachers or administrators may record attendance");
        }
        Staff teacher = staffRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                .filter(staff -> staff.getStatus() == StaffStatus.ACTIVE && staff.isActive())
                .orElseThrow(() -> forbidden("ACTIVE_TEACHER_REQUIRED", "An active teacher record is required"));
        return teacher;
    }

    private void validateEditableDate(Long schoolId, LocalDate attendanceDate, UserPrincipal principal) {
        if (isAdmin(principal)) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(schoolZone(schoolId)));
        if (attendanceDate.isBefore(today)) {
            throw forbidden("PAST_ATTENDANCE_LOCKED", "Only administrators may modify attendance for a past school day");
        }
        if (attendanceDate.isAfter(today)) {
            throw forbidden("FUTURE_ATTENDANCE_LOCKED", "Attendance cannot be recorded for a future school day");
        }
    }

    private ZoneId schoolZone(Long schoolId) {
        School school = schoolRepository.findById(schoolId).orElseThrow(() -> notFound("School not found"));
        try {
            return ZoneId.of(school.getTimezone());
        } catch (RuntimeException exception) {
            throw bad("INVALID_SCHOOL_TIMEZONE", "School timezone is invalid");
        }
    }

    private Long schoolId(Long explicitSchoolId, UserPrincipal principal) {
        if (principal == null) {
            throw forbidden("SCHOOL_REQUIRED", "School context is required");
        }
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId == null) {
                throw bad("SCHOOL_REQUIRED", "SUPER_ADMIN must specify schoolId");
            }
            if (!schoolRepository.existsById(explicitSchoolId)) {
                throw notFound("School not found");
            }
            return explicitSchoolId;
        }
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) {
            throw forbidden("SCHOOL_REQUIRED", "School context is required");
        }
        if (explicitSchoolId != null && !explicitSchoolId.equals(schoolId)) {
            throw notFound("Resource not found");
        }
        return schoolId;
    }

    private void requireRecorder(UserPrincipal principal) {
        if (principal == null || (principal.getRole() != Role.SUPER_ADMIN
                && principal.getRole() != Role.SCHOOL_ADMIN && principal.getRole() != Role.TEACHER)) {
            throw forbidden("ACCESS_DENIED", "Administrative or teacher access is required");
        }
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getRole() == Role.SUPER_ADMIN || principal.getRole() == Role.SCHOOL_ADMIN;
    }

    private String cleanRemarks(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return null;
        }
        return remarks.trim();
    }

    private StudentAttendanceView view(StudentAttendance row, Student student) {
        return new StudentAttendanceView(row.getId(), row.getStudentId(),
                student == null ? null : student.getFullName(), row.getStreamId(), row.getAttendanceDate(),
                row.getAttendanceType(), row.getTimetableLessonId(), row.getStatus(), row.getRemarks(), row.getUpdatedAt());
    }

    private AttendanceRuleException notFound(String message) {
        return new AttendanceRuleException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    private AttendanceRuleException bad(String code, String message) {
        return new AttendanceRuleException(code, HttpStatus.BAD_REQUEST, message);
    }

    private AttendanceRuleException conflict(String code, String message) {
        return new AttendanceRuleException(code, HttpStatus.CONFLICT, message);
    }

    private AttendanceRuleException forbidden(String code, String message) {
        return new AttendanceRuleException(code, HttpStatus.FORBIDDEN, message);
    }
}
