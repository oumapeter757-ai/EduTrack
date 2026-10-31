package com.peterscode.edutrack.examination.dto;

public record MyRegistrationView(
        Long id, String seriesName, String subjectName,
        String candidateNumber, String status
) {}
