package com.peterscode.edutrack.examination.dto;

import java.time.LocalDate;

public record ExamSeriesView(
        Long id, String name, String termName, Long termId,
        LocalDate startDate, LocalDate endDate,
        LocalDate registrationOpensOn, LocalDate registrationClosesOn,
        String status
) {}
