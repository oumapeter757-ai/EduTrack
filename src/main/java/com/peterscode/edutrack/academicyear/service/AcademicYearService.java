package com.peterscode.edutrack.academicyear.service;

import com.peterscode.edutrack.academicyear.dto.AcademicYearResponseDto;
import com.peterscode.edutrack.academicyear.dto.CreateAcademicYearRequestDto;
import com.peterscode.edutrack.academicyear.dto.UpdateAcademicYearRequestDto;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AcademicYearService {

    AcademicYearResponseDto createAcademicYear(CreateAcademicYearRequestDto requestDto, UserPrincipal principal);

    AcademicYearResponseDto getAcademicYearById(Long id, UserPrincipal principal);

    AcademicYearResponseDto getCurrentAcademicYear(UserPrincipal principal);

    PageResponse<AcademicYearResponseDto> getAcademicYears(Long schoolId, Pageable pageable, UserPrincipal principal);

    AcademicYearResponseDto updateAcademicYear(Long id, UpdateAcademicYearRequestDto requestDto, UserPrincipal principal);

    AcademicYearResponseDto setCurrentAcademicYear(Long id, UserPrincipal principal);

    void deleteAcademicYear(Long id, UserPrincipal principal);
}
