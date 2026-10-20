package com.peterscode.edutrack.subject.mapper;

import com.peterscode.edutrack.subject.dto.CreateSubjectRequestDto;
import com.peterscode.edutrack.subject.dto.SubjectResponseDto;
import com.peterscode.edutrack.subject.dto.SubjectSummaryDto;
import com.peterscode.edutrack.subject.dto.UpdateSubjectRequestDto;
import com.peterscode.edutrack.subject.entity.Subject;
import org.springframework.stereotype.Component;

@Component
public class SubjectMapper {

    public Subject toEntity(CreateSubjectRequestDto dto, Long schoolId) {
        if (dto == null) {
            return null;
        }

        return Subject.builder()
                .schoolId(schoolId)
                .classLevelId(dto.getClassLevelId())
                .name(dto.getName() != null ? dto.getName().trim() : null)
                .code(dto.getCode() != null ? dto.getCode().trim().toUpperCase() : null)
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .department(dto.getDepartment() != null ? dto.getDepartment().trim() : null)
                .creditHours(dto.getCreditHours())
                .isElective(Boolean.TRUE.equals(dto.getIsElective()))
                .active(true)
                .build();
    }

    public SubjectResponseDto toDto(Subject subject) {
        return toDto(subject, null);
    }

    public SubjectResponseDto toDto(Subject subject, String classLevelName) {
        if (subject == null) {
            return null;
        }

        return SubjectResponseDto.builder()
                .id(subject.getId())
                .schoolId(subject.getSchoolId())
                .classLevelId(subject.getClassLevelId())
                .classLevelName(classLevelName)
                .name(subject.getName())
                .code(subject.getCode())
                .description(subject.getDescription())
                .department(subject.getDepartment())
                .creditHours(subject.getCreditHours())
                .isElective(subject.isElective())
                .active(subject.isActive())
                .createdAt(subject.getCreatedAt())
                .updatedAt(subject.getUpdatedAt())
                .build();
    }

    public SubjectSummaryDto toSummaryDto(Subject subject) {
        if (subject == null) {
            return null;
        }

        return SubjectSummaryDto.builder()
                .id(subject.getId())
                .name(subject.getName())
                .code(subject.getCode())
                .department(subject.getDepartment())
                .isElective(subject.isElective())
                .active(subject.isActive())
                .build();
    }

    public void updateEntityFromDto(UpdateSubjectRequestDto dto, Subject subject) {
        if (dto == null || subject == null) {
            return;
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            subject.setName(dto.getName().trim());
        }
        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            subject.setCode(dto.getCode().trim().toUpperCase());
        }
        if (dto.getClassLevelId() != null) {
            subject.setClassLevelId(dto.getClassLevelId());
        }
        if (dto.getDescription() != null) {
            subject.setDescription(dto.getDescription().trim());
        }
        if (dto.getDepartment() != null) {
            subject.setDepartment(dto.getDepartment().trim());
        }
        if (dto.getCreditHours() != null) {
            subject.setCreditHours(dto.getCreditHours());
        }
        if (dto.getIsElective() != null) {
            subject.setIsElective(dto.getIsElective());
        }
        if (dto.getActive() != null) {
            subject.setActive(dto.getActive());
        }
    }
}
