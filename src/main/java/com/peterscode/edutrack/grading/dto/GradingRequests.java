package com.peterscode.edutrack.grading.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public class GradingRequests {

    public record CreateGradeScaleRequest(
            @NotBlank String name,
            @NotNull Long academicYearId,
            @NotEmpty List<Range> ranges
    ) {
        public record Range(
                @NotBlank String letterGrade,
                @NotNull BigDecimal gpa,
                @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minPercentage,
                @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal maxPercentage,
                String description,
                Integer sortOrder
        ) {}
    }

    public record CreateAssessmentTypeRequest(
            @NotBlank String name,
            @NotBlank String code
    ) {}

    public record CreateAssessmentPaperRequest(
            @NotBlank String name,
            @NotBlank String code,
            @NotNull @DecimalMin("0") BigDecimal weight,
            @NotNull @DecimalMin("0.01") BigDecimal maxScore
    ) {}

    public record ConfigureSubjectAssessmentsRequest(
            @NotNull Long subjectId,
            @NotEmpty List<@NotNull Long> assessmentTypeIds
    ) {}

    public record ScoreEntry(
            @NotNull Long studentId,
            @NotNull BigDecimal score,
            String remarks
    ) {}

    public record SubmitScoresRequest(
            @NotNull Long termId,
            @NotNull Long streamId,
            @NotNull Long subjectId,
            Long assessmentPaperId,
            @NotEmpty List<@NotNull ScoreEntry> entries
    ) {}
}
