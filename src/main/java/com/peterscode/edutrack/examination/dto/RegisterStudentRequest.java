package com.peterscode.edutrack.examination.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RegisterStudentRequest(
        @NotNull Long studentId,
        @NotNull Long subjectId,
        @NotNull Long assessmentPaperId
) {}
