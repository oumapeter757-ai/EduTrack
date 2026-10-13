package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.dto.*;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.mapper.SchoolMapper;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.response.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;

    @Transactional
    @Override
    public SchoolResponseDto createSchool(CreateSchoolRequestDto requestDto) {
        String normalizedCode = requestDto.getCode().toUpperCase().trim();
        String normalizedSubdomain = requestDto.getSubdomain().toLowerCase().trim();

        if (schoolRepository.existsByCode(normalizedCode)) {
            throw new DuplicateResourceException("School", "code", requestDto.getCode());
        }
        if (schoolRepository.existsBySubdomain(normalizedSubdomain)) {
            throw new DuplicateResourceException("School", "subdomain", requestDto.getSubdomain());
        }

        School school = SchoolMapper.toEntity(requestDto);
        School savedSchool = schoolRepository.save(school);
        log.info("Created new school: {} ({}) with subdomain: {}", savedSchool.getName(), savedSchool.getCode(), savedSchool.getSubdomain());

        return schoolMapper.toDto(savedSchool);
    }

    @Transactional
    @Override
    public SchoolResponseDto updateSchool(Long id, CreateSchoolRequestDto requestDto) {
        School existing = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));

        String normalizedCode = requestDto.getCode().toUpperCase().trim();
        String normalizedSubdomain = requestDto.getSubdomain().toLowerCase().trim();

        if (schoolRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new DuplicateResourceException("School", "code", requestDto.getCode());
        }
        if (schoolRepository.existsBySubdomainAndIdNot(normalizedSubdomain, id)) {
            throw new DuplicateResourceException("School", "subdomain", requestDto.getSubdomain());
        }

        SchoolMapper.updateEntityFromDto(requestDto, existing);
        School updatedSchool = schoolRepository.save(existing);
        log.info("Updated school details for ID: {}", id);

        return schoolMapper.toDto(updatedSchool);
    }

    @Transactional
    @Override
    public SchoolResponseDto updateSchoolProfile(Long id, UpdateSchoolProfileRequestDto requestDto) {
        School existing = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));

        SchoolMapper.updateEntityFromProfileDto(requestDto, existing);
        School updatedSchool = schoolRepository.save(existing);
        log.info("Updated profile for school ID: {}", id);

        return schoolMapper.toDto(updatedSchool);
    }

    @Transactional
    @Override
    public SchoolResponseDto updateSchoolSubscription(Long id, UpdateSchoolSubscriptionRequestDto requestDto) {
        School existing = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));

        SchoolMapper.updateEntityFromSubscriptionDto(requestDto, existing);
        School updatedSchool = schoolRepository.save(existing);
        log.info("Updated subscription for school ID: {} to tier: {}", id, requestDto.getSubscriptionTier());

        return schoolMapper.toDto(updatedSchool);
    }

    @Transactional(readOnly = true)
    @Override
    public SchoolResponseDto getSchoolById(Long id) {
        return schoolRepository.findById(id)
                .map(schoolMapper::toDto)
                .orElseThrow(() -> new SchoolNotFoundException(id));
    }

    @Transactional(readOnly = true)
    @Override
    public SchoolResponseDto getSchoolByCode(String code) {
        return schoolRepository.findByCode(code.toUpperCase().trim())
                .map(schoolMapper::toDto)
                .orElseThrow(() -> SchoolNotFoundException.byCode(code));
    }

    @Transactional(readOnly = true)
    @Override
    public PublicSchoolProfileDto getPublicSchoolBySubdomain(String subdomain) {
        return schoolRepository.findBySubdomain(subdomain.toLowerCase().trim())
                .map(schoolMapper::toPublicDto)
                .orElseThrow(() -> SchoolNotFoundException.bySubdomain(subdomain));
    }

    @Transactional(readOnly = true)
    @Override
    public SchoolResponseDto getSchoolBySubdomain(String subdomain) {
        return schoolRepository.findBySubdomain(subdomain.toLowerCase().trim())
                .map(schoolMapper::toDto)
                .orElseThrow(() -> SchoolNotFoundException.bySubdomain(subdomain));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<SchoolResponseDto> getAllSchools(String search, SubscriptionTier tier, Boolean active, Pageable pageable) {
        Specification<School> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("city")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern)
                ));
            }

            if (tier != null) {
                predicates.add(criteriaBuilder.equal(root.get("subscriptionTier"), tier));
            }

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<SchoolResponseDto> page = schoolRepository.findAll(spec, pageable)
                .map(schoolMapper::toDto);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<SchoolResponseDto> getAllSchools(Pageable pageable) {
        return getAllSchools(null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public SchoolResponseDto getCurrentSchool(Long schoolId) {
        if (schoolId == null) {
            throw new SchoolNotFoundException("Current user has no associated school");
        }
        return getSchoolById(schoolId);
    }

    @Transactional
    @Override
    public void toggleSchoolStatus(Long id, boolean active) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));
        school.setActive(active);
        schoolRepository.save(school);
        log.info("School ID: {} active status changed to: {}", id, active);
    }

    @Transactional
    @Override
    public SchoolResponseDto updateEnabledModules(Long id, Set<ServiceModule> modules) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException(id));
        school.setEnabledModules(modules != null ? new HashSet<>(modules) : new HashSet<>());
        School savedSchool = schoolRepository.save(school);
        log.info("Updated enabled modules for school ID: {} - modules: {}", id, modules);
        return schoolMapper.toDto(savedSchool);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isModuleEnabled(Long schoolId, ServiceModule module) {
        if (schoolId == null || module == null) {
            return false;
        }
        return schoolRepository.findById(schoolId)
                .map(school -> school.isModuleEnabled(module))
                .orElse(false);
    }
}
