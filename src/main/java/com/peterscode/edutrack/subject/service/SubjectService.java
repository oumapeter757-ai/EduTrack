package com.peterscode.edutrack.subject.service;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.subject.dto.CreateSubjectRequestDto;
import com.peterscode.edutrack.subject.dto.SubjectResponseDto;
import com.peterscode.edutrack.subject.dto.SubjectSummaryDto;
import com.peterscode.edutrack.subject.dto.UpdateSubjectRequestDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SubjectService {

    SubjectResponseDto createSubject(CreateSubjectRequestDto requestDto, UserPrincipal principal);

    SubjectResponseDto getSubjectById(Long id, UserPrincipal principal);

    PageResponse<SubjectResponseDto> getSubjects(
            Long schoolId,
            Long classLevelId,
            String department,
            Pageable pageable,
            UserPrincipal principal
    );

    List<SubjectSummaryDto> getActiveSubjects(Long schoolId, UserPrincipal principal);

    List<SubjectResponseDto> getSubjectsByClassLevel(Long classLevelId, UserPrincipal principal);

    SubjectResponseDto updateSubject(Long id, UpdateSubjectRequestDto requestDto, UserPrincipal principal);

    void deleteSubject(Long id, UserPrincipal principal);
}
