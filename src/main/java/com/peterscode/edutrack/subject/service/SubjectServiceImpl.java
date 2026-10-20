package com.peterscode.edutrack.subject.service;

import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;
import com.peterscode.edutrack.shared.exception.SchoolNotFoundException;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.subject.dto.CreateSubjectRequestDto;
import com.peterscode.edutrack.subject.dto.SubjectResponseDto;
import com.peterscode.edutrack.subject.dto.SubjectSummaryDto;
import com.peterscode.edutrack.subject.dto.UpdateSubjectRequestDto;
import com.peterscode.edutrack.subject.entity.Subject;
import com.peterscode.edutrack.subject.exception.SubjectNotFoundException;
import com.peterscode.edutrack.subject.mapper.SubjectMapper;
import com.peterscode.edutrack.subject.repository.SubjectRepository;
import com.peterscode.edutrack.subject.repository.SubjectSpecification;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final SchoolRepository schoolRepository;
    private final ClassLevelRepository classLevelRepository;
    private final SubjectMapper subjectMapper;

    @Transactional
    @Override
    public SubjectResponseDto createSubject(CreateSubjectRequestDto requestDto, UserPrincipal principal) {
        Long schoolId = resolveSchoolIdForCreation(requestDto.getSchoolId(), principal);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));

        if (!school.isActive()) {
            throw new IllegalStateException(String.format("Cannot create subject for inactive school ID: %d", schoolId));
        }

        if (requestDto.getClassLevelId() != null) {
            validateClassLevel(requestDto.getClassLevelId(), schoolId);
        }

        String normalizedCode = requestDto.getCode().trim().toUpperCase();
        if (subjectRepository.existsBySchoolIdAndCode(schoolId, normalizedCode)) {
            throw new DuplicateResourceException("Subject", "code", normalizedCode);
        }

        Subject subject = subjectMapper.toEntity(requestDto, schoolId);
        subject.setCode(normalizedCode);
        Subject savedSubject = subjectRepository.save(subject);
        log.info("Created new subject with ID: {} and code: {} for school ID: {}",
                savedSubject.getId(), savedSubject.getCode(), schoolId);

        String classLevelName = getClassLevelName(savedSubject.getClassLevelId());
        return subjectMapper.toDto(savedSubject, classLevelName);
    }

    @Transactional(readOnly = true)
    @Override
    public SubjectResponseDto getSubjectById(Long id, UserPrincipal principal) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new SubjectNotFoundException(id));

        validateSubjectAccess(subject, principal);

        String classLevelName = getClassLevelName(subject.getClassLevelId());
        return subjectMapper.toDto(subject, classLevelName);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<SubjectResponseDto> getSubjects(
            Long explicitSchoolId,
            Long classLevelId,
            String department,
            Pageable pageable,
            UserPrincipal principal) {
        Long schoolId = resolveSchoolIdForQuery(explicitSchoolId, principal);

        Specification<Subject> spec = SubjectSpecification.withFilters(schoolId, classLevelId, department, null);
        Page<Subject> page = subjectRepository.findAll(spec, pageable);

        Map<Long, String> classLevelNames = new HashMap<>();
        Set<Long> classLevelIds = page.getContent().stream()
                .map(Subject::getClassLevelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!classLevelIds.isEmpty()) {
            classLevelRepository.findAllById(classLevelIds).forEach(cl ->
                    classLevelNames.put(cl.getId(), cl.getName())
            );
        }

        Page<SubjectResponseDto> dtoPage = page.map(subject ->
                subjectMapper.toDto(subject, classLevelNames.get(subject.getClassLevelId()))
        );

        return PageResponse.from(dtoPage);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SubjectSummaryDto> getActiveSubjects(Long explicitSchoolId, UserPrincipal principal) {
        Long schoolId = resolveSchoolIdForQuery(explicitSchoolId, principal);
        if (schoolId == null) {
            throw new IllegalArgumentException("School ID must be specified for this operation");
        }

        return subjectRepository.findBySchoolIdAndActiveTrueOrderByNameAsc(schoolId)
                .stream()
                .map(subjectMapper::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<SubjectResponseDto> getSubjectsByClassLevel(Long classLevelId, UserPrincipal principal) {
        ClassLevel classLevel = classLevelRepository.findById(classLevelId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassLevel", "id", classLevelId));

        if (principal.getRole() != Role.SUPER_ADMIN) {
            Long userSchoolId = principal.getSchoolId();
            if (userSchoolId == null || !classLevel.getSchoolId().equals(userSchoolId)) {
                throw new ClassLevelNotFoundException(classLevelId);
            }
        }

        List<Subject> subjects = subjectRepository.findBySchoolIdAndClassLevelId(classLevel.getSchoolId(), classLevelId);
        return subjects.stream()
                .map(subject -> subjectMapper.toDto(subject, classLevel.getName()))
                .toList();
    }

    @Transactional
    @Override
    public SubjectResponseDto updateSubject(Long id, UpdateSubjectRequestDto requestDto, UserPrincipal principal) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new SubjectNotFoundException(id));

        validateSubjectAccess(subject, principal);

        if (requestDto.getClassLevelId() != null) {
            validateClassLevel(requestDto.getClassLevelId(), subject.getSchoolId());
        }

        if (requestDto.getCode() != null && !requestDto.getCode().isBlank()) {
            String normalizedCode = requestDto.getCode().trim().toUpperCase();
            if (subjectRepository.existsBySchoolIdAndCodeAndIdNot(subject.getSchoolId(), normalizedCode, id)) {
                throw new DuplicateResourceException("Subject", "code", normalizedCode);
            }
            subject.setCode(normalizedCode);
        }

        subjectMapper.updateEntityFromDto(requestDto, subject);
        Subject updatedSubject = subjectRepository.save(subject);
        log.info("Updated subject with ID: {} for school ID: {}", id, subject.getSchoolId());

        String classLevelName = getClassLevelName(updatedSubject.getClassLevelId());
        return subjectMapper.toDto(updatedSubject, classLevelName);
    }

    @Transactional
    @Override
    public void deleteSubject(Long id, UserPrincipal principal) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new SubjectNotFoundException(id));

        validateSubjectAccess(subject, principal);

        subject.setActive(false);
        subjectRepository.save(subject);
        log.info("Soft-deleted subject with ID: {} for school ID: {}", id, subject.getSchoolId());
    }

    private Long resolveSchoolIdForCreation(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId == null) {
                throw new IllegalArgumentException("School ID is required for Super Admin to create a subject");
            }
            return explicitSchoolId;
        }

        Long adminSchoolId = principal.getSchoolId();
        if (adminSchoolId == null) {
            throw new UnauthorizedOperationException("Current user has no associated school");
        }

        if (explicitSchoolId != null && !explicitSchoolId.equals(adminSchoolId)) {
            throw new UnauthorizedOperationException("Cannot create a subject for a different school tenant");
        }

        return adminSchoolId;
    }

    private Long resolveSchoolIdForQuery(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return explicitSchoolId;
        }

        Long userSchoolId = principal.getSchoolId();
        if (userSchoolId == null) {
            throw new UnauthorizedOperationException("Current user has no associated school");
        }

        if (explicitSchoolId != null && !explicitSchoolId.equals(userSchoolId)) {
            throw new SchoolNotFoundException("School not found");
        }

        return userSchoolId;
    }

    private void validateSubjectAccess(Subject subject, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }

        if (principal.getSchoolId() == null || !subject.getSchoolId().equals(principal.getSchoolId())) {
            throw new SubjectNotFoundException(subject.getId());
        }
    }

    private void validateClassLevel(Long classLevelId, Long schoolId) {
        if (classLevelId == null) {
            return;
        }

        ClassLevel classLevel = classLevelRepository.findById(classLevelId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassLevel", "id", classLevelId));

        if (!schoolId.equals(classLevel.getSchoolId())) {
            throw new IllegalArgumentException(String.format("Class level ID %d does not belong to school ID %d", classLevelId, schoolId));
        }
    }

    private String getClassLevelName(Long classLevelId) {
        if (classLevelId == null) {
            return null;
        }

        return classLevelRepository.findById(classLevelId)
                .map(ClassLevel::getName)
                .orElse(null);
    }
}
