package com.peterscode.edutrack.academicyear.service;

import com.peterscode.edutrack.academicyear.dto.AcademicYearResponseDto;
import com.peterscode.edutrack.academicyear.dto.CreateAcademicYearRequestDto;
import com.peterscode.edutrack.academicyear.dto.UpdateAcademicYearRequestDto;
import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import com.peterscode.edutrack.academicyear.mapper.AcademicYearMapper;
import com.peterscode.edutrack.academicyear.repository.AcademicYearRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicYearServiceImpl implements AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final SchoolRepository schoolRepository;
    private final AcademicYearMapper academicYearMapper;

    @Transactional
    @Override
    public AcademicYearResponseDto createAcademicYear(CreateAcademicYearRequestDto requestDto, UserPrincipal principal) {
        Long schoolId = resolveSchoolId(requestDto.getSchoolId(), principal);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));
        if (!school.isActive()) {
            throw new IllegalStateException("Cannot create academic year for inactive school");
        }

        validateDateRange(requestDto.getStartDate(), requestDto.getEndDate());

        String name = requestDto.getName().trim();
        if (academicYearRepository.existsBySchoolIdAndName(schoolId, name)) {
            throw new DuplicateResourceException("AcademicYear", "name", name);
        }

        AcademicYear entity = academicYearMapper.toEntity(requestDto, schoolId);
        AcademicYear saved = academicYearRepository.save(entity);
        log.info("Created academic year '{}' (ID: {}) for school ID: {}", saved.getName(), saved.getId(), schoolId);

        return academicYearMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public AcademicYearResponseDto getAcademicYearById(Long id, UserPrincipal principal) {
        AcademicYear entity = academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "id", id));
        validateAccess(entity.getSchoolId(), principal);
        return academicYearMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    @Override
    public AcademicYearResponseDto getCurrentAcademicYear(UserPrincipal principal) {
        Long schoolId = resolveSchoolId(null, principal);
        AcademicYear entity = academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("No current academic year set for this school"));
        return academicYearMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<AcademicYearResponseDto> getAcademicYears(Long schoolId, Pageable pageable, UserPrincipal principal) {
        Long targetSchoolId = resolveSchoolId(schoolId, principal);
        Page<AcademicYearResponseDto> page = academicYearRepository.findBySchoolId(targetSchoolId, pageable)
                .map(academicYearMapper::toDto);
        return PageResponse.from(page);
    }

    @Transactional
    @Override
    public AcademicYearResponseDto updateAcademicYear(Long id, UpdateAcademicYearRequestDto requestDto, UserPrincipal principal) {
        AcademicYear entity = academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "id", id));
        validateAdminAccess(entity.getSchoolId(), principal);

        if (requestDto.getName() != null && !requestDto.getName().isBlank()) {
            String name = requestDto.getName().trim();
            if (academicYearRepository.existsBySchoolIdAndNameAndIdNot(entity.getSchoolId(), name, id)) {
                throw new DuplicateResourceException("AcademicYear", "name", name);
            }
        }

        academicYearMapper.updateEntityFromDto(requestDto, entity);
        validateDateRange(entity.getStartDate(), entity.getEndDate());

        AcademicYear saved = academicYearRepository.save(entity);
        log.info("Updated academic year ID: {} for school ID: {}", id, entity.getSchoolId());
        return academicYearMapper.toDto(saved);
    }

    @Transactional
    @Override
    public AcademicYearResponseDto setCurrentAcademicYear(Long id, UserPrincipal principal) {
        AcademicYear entity = academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "id", id));
        validateAdminAccess(entity.getSchoolId(), principal);

        academicYearRepository.unsetCurrentForSchool(entity.getSchoolId());
        entity.setCurrent(true);
        AcademicYear saved = academicYearRepository.save(entity);
        log.info("Set academic year '{}' (ID: {}) as current for school ID: {}", saved.getName(), id, entity.getSchoolId());
        return academicYearMapper.toDto(saved);
    }

    @Transactional
    @Override
    public void deleteAcademicYear(Long id, UserPrincipal principal) {
        AcademicYear entity = academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "id", id));
        validateAdminAccess(entity.getSchoolId(), principal);

        entity.setActive(false);
        entity.setCurrent(false);
        academicYearRepository.save(entity);
        log.info("Soft deleted academic year ID: {} for school ID: {}", id, entity.getSchoolId());
    }

    private Long resolveSchoolId(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId != null) return explicitSchoolId;
            throw new IllegalArgumentException("School ID must be specified for this operation");
        }
        Long adminSchoolId = principal.getSchoolId();
        if (adminSchoolId == null) {
            throw new UnauthorizedOperationException("Current user has no associated school");
        }
        if (explicitSchoolId != null && !explicitSchoolId.equals(adminSchoolId)) {
            throw new UnauthorizedOperationException("Cannot operate on a different school tenant");
        }
        return adminSchoolId;
    }

    private void validateAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) return;
        if (!schoolId.equals(principal.getSchoolId())) {
            throw new UnauthorizedOperationException("Cannot access resource from another school tenant");
        }
    }

    private void validateAdminAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) return;
        if (principal.getRole() != Role.SCHOOL_ADMIN || !schoolId.equals(principal.getSchoolId())) {
            throw new UnauthorizedOperationException("You do not have administrative permission for this school");
        }
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}
