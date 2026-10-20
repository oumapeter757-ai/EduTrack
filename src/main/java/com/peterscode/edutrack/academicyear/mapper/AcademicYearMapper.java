package com.peterscode.edutrack.academicyear.mapper;

import com.peterscode.edutrack.academicyear.dto.AcademicYearResponseDto;
import com.peterscode.edutrack.academicyear.dto.CreateAcademicYearRequestDto;
import com.peterscode.edutrack.academicyear.dto.UpdateAcademicYearRequestDto;
import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import org.springframework.stereotype.Component;

@Component
public class AcademicYearMapper {

    public AcademicYear toEntity(CreateAcademicYearRequestDto dto, Long schoolId) {
        return AcademicYear.builder()
                .schoolId(schoolId)
                .name(dto.getName().trim())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .current(false)
                .active(true)
                .build();
    }

    public AcademicYearResponseDto toDto(AcademicYear entity) {
        return AcademicYearResponseDto.builder()
                .id(entity.getId())
                .schoolId(entity.getSchoolId())
                .name(entity.getName())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .current(entity.isCurrent())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntityFromDto(UpdateAcademicYearRequestDto dto, AcademicYear entity) {
        if (dto.getName() != null && !dto.getName().isBlank()) {
            entity.setName(dto.getName().trim());
        }
        if (dto.getStartDate() != null) {
            entity.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            entity.setEndDate(dto.getEndDate());
        }
    }
}
