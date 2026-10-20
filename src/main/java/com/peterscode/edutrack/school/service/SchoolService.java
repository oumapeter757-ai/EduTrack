package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface SchoolService {

    SchoolResponseDto createSchool(CreateSchoolRequestDto requestDto);

    SchoolResponseDto updateSchool(Long id, UpdateSchoolRequestDto requestDto);

    SchoolResponseDto updateSchoolProfile(Long id, UpdateSchoolProfileRequestDto requestDto,
                                          UserPrincipal principal);

    SchoolResponseDto updateSchoolSubscription(Long id, UpdateSchoolSubscriptionRequestDto requestDto);

    SchoolResponseDto getSchoolById(Long id, UserPrincipal principal);

    PublicSchoolProfileDto getSchoolByCode(String code);

    PublicSchoolProfileDto getPublicSchoolBySubdomain(String subdomain);

    PageResponse<SchoolResponseDto> getAllSchools(String search, SubscriptionTier tier, Boolean active, Pageable pageable);

    PageResponse<SchoolResponseDto> getAllSchools(Pageable pageable);

    SchoolResponseDto getCurrentSchool(Long schoolId);

    void toggleSchoolStatus(Long id, boolean active);

    SchoolResponseDto updateEnabledModules(Long id, Set<ServiceModule> modules);

    boolean isModuleEnabled(Long schoolId, ServiceModule module);
}