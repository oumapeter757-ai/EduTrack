package com.peterscode.edutrack.classlevel.mapper;

import com.peterscode.edutrack.classlevel.dto.*;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.entity.Stream;
import org.springframework.stereotype.Component;

@Component
public class ClassLevelMapper {

    public ClassLevel toEntity(CreateClassLevelRequestDto dto, Long schoolId) {
        return ClassLevel.builder()
                .schoolId(schoolId)
                .name(dto.getName().trim())
                .code(dto.getCode().trim().toUpperCase())
                .description(dto.getDescription())
                .sortOrder(dto.getSortOrder())
                .active(true)
                .build();
    }

    public ClassLevelResponseDto toDto(ClassLevel entity) {
        return ClassLevelResponseDto.builder()
                .id(entity.getId())
                .schoolId(entity.getSchoolId())
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntityFromDto(UpdateClassLevelRequestDto dto, ClassLevel entity) {
        if (dto.getName() != null && !dto.getName().isBlank()) entity.setName(dto.getName().trim());
        if (dto.getCode() != null && !dto.getCode().isBlank()) entity.setCode(dto.getCode().trim().toUpperCase());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
    }

    public Stream toStreamEntity(CreateStreamRequestDto dto, Long schoolId) {
        return Stream.builder()
                .schoolId(schoolId)
                .classLevelId(dto.getClassLevelId())
                .name(dto.getName().trim())
                .code(dto.getCode().trim().toUpperCase())
                .capacity(dto.getCapacity())
                .active(true)
                .build();
    }

    public StreamResponseDto toStreamDto(Stream entity, String classLevelName) {
        return StreamResponseDto.builder()
                .id(entity.getId())
                .schoolId(entity.getSchoolId())
                .classLevelId(entity.getClassLevelId())
                .classLevelName(classLevelName)
                .name(entity.getName())
                .code(entity.getCode())
                .capacity(entity.getCapacity())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateStreamFromDto(UpdateStreamRequestDto dto, Stream entity) {
        if (dto.getName() != null && !dto.getName().isBlank()) entity.setName(dto.getName().trim());
        if (dto.getCode() != null && !dto.getCode().isBlank()) entity.setCode(dto.getCode().trim().toUpperCase());
        if (dto.getCapacity() != null) entity.setCapacity(dto.getCapacity());
    }
}
