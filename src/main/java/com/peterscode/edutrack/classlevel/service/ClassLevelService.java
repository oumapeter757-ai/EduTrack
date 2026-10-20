package com.peterscode.edutrack.classlevel.service;

import com.peterscode.edutrack.classlevel.dto.*;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClassLevelService {
    ClassLevelResponseDto createClassLevel(CreateClassLevelRequestDto requestDto, UserPrincipal principal);
    ClassLevelResponseDto getClassLevelById(Long id, UserPrincipal principal);
    PageResponse<ClassLevelResponseDto> getClassLevels(Long schoolId, Pageable pageable, UserPrincipal principal);
    List<ClassLevelResponseDto> getActiveClassLevels(UserPrincipal principal);
    ClassLevelResponseDto updateClassLevel(Long id, UpdateClassLevelRequestDto requestDto, UserPrincipal principal);
    void deleteClassLevel(Long id, UserPrincipal principal);
}
