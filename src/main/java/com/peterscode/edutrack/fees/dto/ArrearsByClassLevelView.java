package com.peterscode.edutrack.fees.dto;

import java.math.BigDecimal;

/**
 * Arrears report summary grouped by class level.
 */
public record ArrearsByClassLevelView(
        Long classLevelId,
        String classLevelName,
        int studentCount,
        BigDecimal totalArrears,
        String currency
) {}
