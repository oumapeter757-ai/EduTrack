package com.peterscode.edutrack.lms.dto;

public class LmsViews {

    public record CourseView(
            Long id,
            String title,
            String status,
            Long streamSubjectId,
            Long createdBy,
            String createdAt
    ) {
    }

    public record TopicView(
            Long id,
            String title,
            Integer position
    ) {
    }

    public record ResourceView(
            Long id,
            String title,
            String type,
            String body,
            String url,
            Boolean published,
            String visibleFrom
    ) {
    }

    public record AssignmentView(
            Long id,
            String title,
            String instructions,
            String opensAt,
            String dueAt,
            String maxScore,
            String latePolicy,
            String status
    ) {
    }

    public record SubmissionView(
            Long id,
            Long assignmentId,
            String answerText,
            String submittedAt,
            Boolean late,
            Integer attemptCount,
            String status,
            String score,
            String feedback,
            Boolean released
    ) {
    }

    public record AnnouncementView(
            Long id,
            String title,
            String body,
            Long postedBy,
            String createdAt
    ) {
    }

    public record CreateAnnouncementRequest(
            String title,
            String body
    ) {
    }
}
