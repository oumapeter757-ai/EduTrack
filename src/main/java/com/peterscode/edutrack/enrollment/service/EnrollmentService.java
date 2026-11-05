package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.enrollment.dto.EnrollmentView;
import com.peterscode.edutrack.enrollment.entity.EnrollmentStatus;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface EnrollmentService {
    void recordAdmission(Student student);
    void synchronizePlacement(Student student, Long oldYearId, Long oldClassId, Long oldStreamId);
    void synchronizeStatus(Student student, StudentStatus oldStatus, UserPrincipal principal);
    PageResponse<EnrollmentView> history(Long studentId, Long requestedSchoolId, Pageable pageable, UserPrincipal principal);
}
