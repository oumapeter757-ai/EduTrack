package com.peterscode.edutrack.classlevel.service;

import com.peterscode.edutrack.classlevel.dto.*;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.exception.ClassLevelNotFoundException;
import com.peterscode.edutrack.classlevel.mapper.ClassLevelMapper;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassLevelServiceImpl implements ClassLevelService {

    private final ClassLevelRepository classLevelRepository;
    private final SchoolRepository schoolRepository;
    private final ClassLevelMapper classLevelMapper;

    @Transactional
    @Override
    public ClassLevelResponseDto createClassLevel(CreateClassLevelRequestDto requestDto, UserPrincipal principal) {
        Long schoolId = resolveSchoolId(requestDto.getSchoolId(), principal);
        schoolRepository.findById(schoolId).filter(s -> s.isActive())
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));

        String code = requestDto.getCode().trim().toUpperCase();
        if (classLevelRepository.existsBySchoolIdAndCode(schoolId, code)) {
            throw new DuplicateResourceException("ClassLevel", "code", code);
        }

        ClassLevel entity = classLevelMapper.toEntity(requestDto, schoolId);
        ClassLevel saved = classLevelRepository.save(entity);
        log.info("Created class level '{}' (ID: {}) for school ID: {}", saved.getName(), saved.getId(), schoolId);
        return classLevelMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public ClassLevelResponseDto getClassLevelById(Long id, UserPrincipal principal) {
        ClassLevel entity = classLevelRepository.findById(id)
                .orElseThrow(() -> new ClassLevelNotFoundException(id));
        validateAccess(entity.getSchoolId(), principal);
        return classLevelMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ClassLevelResponseDto> getClassLevels(Long schoolId, Pageable pageable, UserPrincipal principal) {
        Long targetSchoolId = resolveSchoolId(schoolId, principal);
        return PageResponse.from(classLevelRepository.findBySchoolId(targetSchoolId, pageable)
                .map(classLevelMapper::toDto));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ClassLevelResponseDto> getActiveClassLevels(UserPrincipal principal) {
        Long schoolId = resolveSchoolId(null, principal);
        return classLevelRepository.findBySchoolIdAndActiveTrueOrderBySortOrderAsc(schoolId)
                .stream().map(classLevelMapper::toDto).toList();
    }

    @Transactional
    @Override
    public ClassLevelResponseDto updateClassLevel(Long id, UpdateClassLevelRequestDto requestDto, UserPrincipal principal) {
        ClassLevel entity = classLevelRepository.findById(id)
                .orElseThrow(() -> new ClassLevelNotFoundException(id));
        validateAdminAccess(entity.getSchoolId(), principal);

        if (requestDto.getCode() != null && !requestDto.getCode().isBlank()) {
            String code = requestDto.getCode().trim().toUpperCase();
            if (classLevelRepository.existsBySchoolIdAndCodeAndIdNot(entity.getSchoolId(), code, id)) {
                throw new DuplicateResourceException("ClassLevel", "code", code);
            }
        }

        classLevelMapper.updateEntityFromDto(requestDto, entity);
        ClassLevel saved = classLevelRepository.save(entity);
        log.info("Updated class level ID: {} for school ID: {}", id, entity.getSchoolId());
        return classLevelMapper.toDto(saved);
    }

    @Transactional
    @Override
    public void deleteClassLevel(Long id, UserPrincipal principal) {
        ClassLevel entity = classLevelRepository.findById(id)
                .orElseThrow(() -> new ClassLevelNotFoundException(id));
        validateAdminAccess(entity.getSchoolId(), principal);
        entity.setActive(false);
        classLevelRepository.save(entity);
        log.info("Soft deleted class level ID: {} for school ID: {}", id, entity.getSchoolId());
    }

    private Long resolveSchoolId(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId != null) return explicitSchoolId;
            throw new IllegalArgumentException("School ID must be specified");
        }
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) throw new UnauthorizedOperationException("Current user has no associated school");
        if (explicitSchoolId != null && !explicitSchoolId.equals(schoolId))
            throw new UnauthorizedOperationException("Cannot operate on a different school tenant");
        return schoolId;
    }

    private void validateAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) return;
        if (!schoolId.equals(principal.getSchoolId()))
            throw new UnauthorizedOperationException("Cannot access resource from another school tenant");
    }

    private void validateAdminAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) return;
        if (principal.getRole() != Role.SCHOOL_ADMIN || !schoolId.equals(principal.getSchoolId()))
            throw new UnauthorizedOperationException("You do not have administrative permission for this school");
    }
}
