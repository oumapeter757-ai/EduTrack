package com.peterscode.edutrack.examination.dto;

public record ExamRegistrationView(
        Long id, String seriesName, Long studentId, String studentName,
        String subjectName, String candidateNumber, String status, String reason
) {}
