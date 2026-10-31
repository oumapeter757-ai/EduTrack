package com.peterscode.edutrack.examination.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateStatusRequest(
        @NotNull com.peterscode.edutrack.examination.entity.ExamSeries.SeriesStatus status
) {}
