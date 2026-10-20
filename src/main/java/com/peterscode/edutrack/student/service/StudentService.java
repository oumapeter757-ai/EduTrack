package com.peterscode.edutrack.student.service;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.dto.*;
import org.springframework.data.domain.Pageable;

public interface StudentService {

    StudentResponseDto createStudent(CreateStudentRequestDto requestDto, UserPrincipal principal);

    StudentResponseDto getStudentById(Long id, UserPrincipal principal);

    StudentResponseDto getStudentByAdmissionNumber(String admissionNumber, UserPrincipal principal);

    StudentResponseDto getStudentByAdmissionNumber(String admissionNumber, Long schoolId, UserPrincipal principal);

    StudentResponseDto getCurrentStudentProfile(UserPrincipal principal);

    StudentResponseDto updateStudent(Long id, UpdateStudentRequestDto requestDto, UserPrincipal principal);

    StudentResponseDto updateStudentStatus(Long id, UpdateStudentStatusRequestDto requestDto, UserPrincipal principal);

    void bulkUpdateStatus(BulkStudentStatusUpdateRequestDto requestDto, UserPrincipal principal);

    void deleteStudent(Long id, UserPrincipal principal);

    PageResponse<StudentSummaryDto> searchStudents(StudentSearchCriteria criteria, Pageable pageable, UserPrincipal principal);

    StudentStatsDto getStudentStatistics(Long schoolId, UserPrincipal principal);

    StudentResponseDto linkUserAccount(Long studentId, Long userId, UserPrincipal principal);

    StudentResponseDto unlinkUserAccount(Long studentId, UserPrincipal principal);
}
