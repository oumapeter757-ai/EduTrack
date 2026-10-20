package com.peterscode.edutrack.academicyear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAcademicYearRequestDto {
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
}
