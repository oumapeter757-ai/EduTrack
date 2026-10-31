package com.peterscode.edutrack.examination.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record BulkRegisterRequest(
        Long streamId,
        String classLevelId,
        @NotEmpty List<Long> assessmentPaperIds
) {}
