package com.peterscode.edutrack.academicyear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicYearResponseDto {
    private Long id;
    private Long schoolId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean current;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
