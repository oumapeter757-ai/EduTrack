package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.dto.LmsRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.CourseStatus;
import com.peterscode.edutrack.lms.entity.LmsCourse;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsCourseRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.timetable.entity.StreamSubject;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LmsCourseService {

    private final LmsCourseRepository courseRepository;
    private final StreamSubjectRepository streamSubjectRepository;
    private final LmsAuditService auditService;
    private final LmsAccessGuard accessGuard;

    @Transactional
    public LmsViews.CourseView createCourse(Long schoolId, LmsRequests.CreateCourseRequest request, UserPrincipal principal) {
        if (principal.getRole() != Role.TEACHER) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Only teachers can create courses");
        }

        StreamSubject streamSubject = streamSubjectRepository.findById(request.streamSubjectId())
                .orElseThrow(() -> new LmsRuleException("STREAM_SUBJECT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Stream subject not found"));

        if (!streamSubject.getSchoolId().equals(schoolId)) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.NOT_FOUND, "Stream subject not found");
        }

        if (!streamSubject.getTeacherId().equals(principal.getId())) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN,
                    "Only the assigned teacher can create a course for this stream-subject");
        }

        if (courseRepository.existsBySchoolIdAndStreamSubjectId(schoolId, request.streamSubjectId())) {
            throw new LmsRuleException("COURSE_EXISTS", HttpStatus.CONFLICT,
                    "A course already exists for this stream-subject");
        }

        LmsCourse course = LmsCourse.builder()
                .schoolId(schoolId)
                .streamSubjectId(request.streamSubjectId())
                .title(request.title())
                .status(CourseStatus.DRAFT)
                .createdBy(principal.getId())
                .build();

        course = courseRepository.save(course);

        auditService.append(schoolId, "COURSE", course.getId(), "CREATE",
                principal.getId(), null, "title:" + request.title(), null);

        return toView(course);
    }

    @Transactional
    public LmsViews.CourseView updateCourse(Long schoolId, Long courseId, LmsRequests.UpdateCourseRequest request, UserPrincipal principal) {
        LmsCourse course = accessGuard.assertCourseTeacher(schoolId, courseId, principal);

        if (request.title() != null) {
            course.setTitle(request.title());
        }
        if (request.status() != null) {
            course.setStatus(CourseStatus.valueOf(request.status()));
        }

        course = courseRepository.save(course);

        auditService.append(schoolId, "COURSE", course.getId(), "UPDATE",
                principal.getId(), null,
                "title:" + course.getTitle() + ",status:" + course.getStatus().name(), null);

        return toView(course);
    }

    @Transactional(readOnly = true)
    public Page<LmsViews.CourseView> listMyCourses(Long schoolId, Pageable pageable, UserPrincipal principal) {
        return courseRepository.findBySchoolId(schoolId, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public LmsViews.CourseView getCourse(Long schoolId, Long courseId, UserPrincipal principal) {
        LmsCourse course;
        if (principal.getRole() == Role.TEACHER) {
            course = accessGuard.assertCourseTeacher(schoolId, courseId, principal);
        } else if (principal.getRole() == Role.STUDENT) {
            course = accessGuard.assertStudentInCourse(schoolId, courseId, principal);
        } else {
            course = accessGuard.assertCourseTeacher(schoolId, courseId, principal);
        }
        return toView(course);
    }

    private LmsViews.CourseView toView(LmsCourse course) {
        return new LmsViews.CourseView(
                course.getId(),
                course.getTitle(),
                course.getStatus().name(),
                course.getStreamSubjectId(),
                course.getCreatedBy(),
                course.getCreatedAt() != null ? course.getCreatedAt().toString() : null
        );
    }
}
