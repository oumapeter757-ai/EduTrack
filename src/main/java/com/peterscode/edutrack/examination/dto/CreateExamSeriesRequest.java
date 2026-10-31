package com.peterscode.edutrack.examination.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateExamSeriesRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Long termId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalDate registrationOpensOn,
        @NotNull LocalDate registrationClosesOn
) {}
