package com.peterscode.edutrack.lms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LmsAssignmentRequests {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateAssignmentRequest(
            @NotBlank @Size(max = 255) String title,
            String instructions,
            @NotNull LocalDateTime opensAt,
            @NotNull LocalDateTime dueAt,
            @NotNull @DecimalMin("0") BigDecimal maxScore,
            String latePolicy
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SubmitAssignmentRequest(
            String answerText
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record MarkSubmissionRequest(
            @NotNull @DecimalMin("0") BigDecimal score,
            @Size(max = 2000) String feedback
    ) {
    }
}
