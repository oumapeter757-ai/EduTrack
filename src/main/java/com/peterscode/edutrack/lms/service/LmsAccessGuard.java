package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.entity.LmsCourse;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsCourseRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.timetable.entity.StreamSubject;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LmsAccessGuard {

    private final LmsCourseRepository courseRepository;
    private final StreamSubjectRepository streamSubjectRepository;
    private final StaffRepository staffRepository;
    private final StudentRepository studentRepository;

    /**
     * Assert that the current user is the teacher assigned to the course's stream-subject.
     */
    @Transactional(readOnly = true)
    public LmsCourse assertCourseTeacher(Long schoolId, Long courseId, UserPrincipal principal) {
        LmsCourse course = courseRepository.findBySchoolIdAndId(schoolId, courseId)
                .orElseThrow(() -> new LmsRuleException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "Course not found"));

        if (principal.getRole() == Role.SCHOOL_ADMIN || principal.getRole() == Role.SUPER_ADMIN) {
            return course;
        }

        if (principal.getRole() != Role.TEACHER) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
        }

        StreamSubject streamSubject = streamSubjectRepository.findById(course.getStreamSubjectId())
                .orElseThrow(() -> new LmsRuleException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "Course not found"));

        if (!streamSubject.getTeacherId().equals(principal.getId())) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
        }

        return course;
    }

    /**
     * Assert that the current user is a student in the course's stream.
     */
    @Transactional(readOnly = true)
    public LmsCourse assertStudentInCourse(Long schoolId, Long courseId, UserPrincipal principal) {
        LmsCourse course = courseRepository.findBySchoolIdAndId(schoolId, courseId)
                .orElseThrow(() -> new LmsRuleException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "Course not found"));

        if (principal.getRole() == Role.SCHOOL_ADMIN || principal.getRole() == Role.SUPER_ADMIN) {
            return course;
        }

        if (principal.getRole() == Role.TEACHER) {
            StreamSubject streamSubject = streamSubjectRepository.findById(course.getStreamSubjectId())
                    .orElseThrow(() -> new LmsRuleException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "Course not found"));
            if (streamSubject.getTeacherId().equals(principal.getId())) {
                return course;
            }
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
        }

        if (principal.getRole() != Role.STUDENT) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
        }

        Optional<Student> student = studentRepository.findById(principal.getId());
        if (student.isEmpty() || student.get().getStatus() != StudentStatus.ACTIVE) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.NOT_FOUND, "Course not found");
        }

        StreamSubject streamSubject = streamSubjectRepository.findById(course.getStreamSubjectId())
                .orElseThrow(() -> new LmsRuleException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "Course not found"));

        if (!student.get().getStreamId().equals(streamSubject.getStreamId())) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.NOT_FOUND, "Course not found");
        }

        return course;
    }

    /**
     * Assert that the current user is a SCHOOL_ADMIN or SUPER_ADMIN.
     */
    public void assertAdmin(UserPrincipal principal) {
        if (principal.getRole() != Role.SCHOOL_ADMIN && principal.getRole() != Role.SUPER_ADMIN) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
