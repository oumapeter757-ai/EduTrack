package com.peterscode.edutrack.grading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class GradingViews {

    public record GradeScaleView(
            Long id,
            String name,
            String academicYearName,
            Boolean active,
            List<GradeScaleRangeView> ranges
    ) {}

    public record GradeScaleRangeView(
            String letterGrade,
            BigDecimal gpa,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            String description
    ) {}

    public record AssessmentTypeView(
            Long id,
            String name,
            String code,
            List<AssessmentPaperView> papers
    ) {}

    public record AssessmentPaperView(
            Long id,
            String name,
            String code,
            BigDecimal weight,
            BigDecimal maxScore
    ) {}

    public record SubjectAssessmentView(
            Long subjectId,
            String subjectName,
            Long assessmentTypeId,
            String assessmentTypeName,
            List<AssessmentPaperView> papers
    ) {}

    public record StudentScoreView(
            Long studentId,
            String studentName,
            String assessmentPaperName,
            BigDecimal score,
            BigDecimal maxScore,
            String remarks,
            LocalDateTime enteredAt
    ) {}

    public record MyScoreView(
            String subjectName,
            String assessmentTypeName,
            String paperName,
            BigDecimal score,
            BigDecimal maxScore
    ) {}

    public record TermGradeView(
            Long studentId,
            String studentName,
            String subjectName,
            BigDecimal totalRawScore,
            BigDecimal totalMaxScore,
            BigDecimal weightedPercentage,
            String letterGrade,
            BigDecimal gpa,
            Integer streamRank
    ) {}

    public record MyTermGradeView(
            String subjectName,
            BigDecimal totalRawScore,
            BigDecimal totalMaxScore,
            BigDecimal weightedPercentage,
            String letterGrade,
            BigDecimal gpa,
            Integer streamRank
    ) {}

    public record ClassStatisticsView(
            String subjectName,
            String streamName,
            Integer studentCount,
            BigDecimal averageScore,
            BigDecimal medianScore,
            BigDecimal highestScore,
            BigDecimal lowestScore,
            BigDecimal passRate
    ) {}

    public record ReportCardView(
            String studentName,
            String streamName,
            String termName,
            List<TermGradeView> grades,
            BigDecimal overallGpa,
            Integer overallRank
    ) {}

    public record SubmitScoresResponse(
            Integer submittedCount,
            Integer updatedCount,
            List<StudentScoreView> scores
    ) {}
}
