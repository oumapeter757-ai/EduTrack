package com.peterscode.edutrack.classlevel.service;

import com.peterscode.edutrack.classlevel.dto.*;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.exception.ClassLevelNotFoundException;
import com.peterscode.edutrack.classlevel.exception.StreamNotFoundException;
import com.peterscode.edutrack.classlevel.mapper.ClassLevelMapper;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.DuplicateResourceException;
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
public class StreamServiceImpl implements StreamService {

    private final StreamRepository streamRepository;
    private final ClassLevelRepository classLevelRepository;
    private final ClassLevelMapper classLevelMapper;

    @Transactional
    @Override
    public StreamResponseDto createStream(CreateStreamRequestDto requestDto, UserPrincipal principal) {
        ClassLevel classLevel = classLevelRepository.findById(requestDto.getClassLevelId())
                .orElseThrow(() -> new ClassLevelNotFoundException(requestDto.getClassLevelId()));

        Long schoolId = resolveSchoolId(requestDto.getSchoolId(), principal);
        if (!classLevel.getSchoolId().equals(schoolId)) {
            throw new UnauthorizedOperationException("Class level does not belong to your school");
        }

        String code = requestDto.getCode().trim().toUpperCase();
        if (streamRepository.existsByClassLevelIdAndCode(classLevel.getId(), code)) {
            throw new DuplicateResourceException("Stream", "code", code);
        }

        Stream entity = classLevelMapper.toStreamEntity(requestDto, schoolId);
        Stream saved = streamRepository.save(entity);
        log.info("Created stream '{}' (ID: {}) for class level ID: {}", saved.getName(), saved.getId(), classLevel.getId());
        return classLevelMapper.toStreamDto(saved, classLevel.getName());
    }

    @Transactional(readOnly = true)
    @Override
    public StreamResponseDto getStreamById(Long id, UserPrincipal principal) {
        Stream entity = streamRepository.findById(id)
                .orElseThrow(() -> new StreamNotFoundException(id));
        validateAccess(entity.getSchoolId(), principal);
        String classLevelName = classLevelRepository.findById(entity.getClassLevelId())
                .map(ClassLevel::getName).orElse(null);
        return classLevelMapper.toStreamDto(entity, classLevelName);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<StreamResponseDto> getStreams(Long schoolId, Long classLevelId, Pageable pageable, UserPrincipal principal) {
        Long targetSchoolId = resolveSchoolId(schoolId, principal);
        return PageResponse.from(streamRepository.findBySchoolId(targetSchoolId, pageable)
                .map(s -> {
                    String clName = classLevelRepository.findById(s.getClassLevelId())
                            .map(ClassLevel::getName).orElse(null);
                    return classLevelMapper.toStreamDto(s, clName);
                }));
    }

    @Transactional(readOnly = true)
    @Override
    public List<StreamResponseDto> getStreamsByClassLevel(Long classLevelId, UserPrincipal principal) {
        ClassLevel classLevel = classLevelRepository.findById(classLevelId)
                .orElseThrow(() -> new ClassLevelNotFoundException(classLevelId));
        validateAccess(classLevel.getSchoolId(), principal);
        return streamRepository.findByClassLevelIdAndActiveTrueOrderByNameAsc(classLevelId)
                .stream().map(s -> classLevelMapper.toStreamDto(s, classLevel.getName())).toList();
    }

    @Transactional
    @Override
    public StreamResponseDto updateStream(Long id, UpdateStreamRequestDto requestDto, UserPrincipal principal) {
        Stream entity = streamRepository.findById(id)
                .orElseThrow(() -> new StreamNotFoundException(id));
        validateAdminAccess(entity.getSchoolId(), principal);

        if (requestDto.getCode() != null && !requestDto.getCode().isBlank()) {
            String code = requestDto.getCode().trim().toUpperCase();
            if (streamRepository.existsByClassLevelIdAndCodeAndIdNot(entity.getClassLevelId(), code, id)) {
                throw new DuplicateResourceException("Stream", "code", code);
            }
        }

        classLevelMapper.updateStreamFromDto(requestDto, entity);
        Stream saved = streamRepository.save(entity);
        String clName = classLevelRepository.findById(entity.getClassLevelId())
                .map(ClassLevel::getName).orElse(null);
        log.info("Updated stream ID: {}", id);
        return classLevelMapper.toStreamDto(saved, clName);
    }

    @Transactional
    @Override
    public void deleteStream(Long id, UserPrincipal principal) {
        Stream entity = streamRepository.findById(id)
                .orElseThrow(() -> new StreamNotFoundException(id));
        validateAdminAccess(entity.getSchoolId(), principal);
        entity.setActive(false);
        streamRepository.save(entity);
        log.info("Soft deleted stream ID: {}", id);
    }

    private Long resolveSchoolId(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId != null) return explicitSchoolId;
            throw new IllegalArgumentException("School ID must be specified");
        }
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) throw new UnauthorizedOperationException("No associated school");
        if (explicitSchoolId != null && !explicitSchoolId.equals(schoolId))
            throw new UnauthorizedOperationException("Cannot operate on a different school tenant");
        return schoolId;
    }

    private void validateAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) return;
        if (!schoolId.equals(principal.getSchoolId()))
            throw new UnauthorizedOperationException("Cannot access resource from another school");
    }

    private void validateAdminAccess(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) return;
        if (principal.getRole() != Role.SCHOOL_ADMIN || !schoolId.equals(principal.getSchoolId()))
            throw new UnauthorizedOperationException("No admin permission for this school");
    }
}
