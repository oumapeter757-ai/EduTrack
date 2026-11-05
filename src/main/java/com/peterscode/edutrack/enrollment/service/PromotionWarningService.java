package com.peterscode.edutrack.enrollment.service;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.List;

public interface PromotionWarningService {
    WarningFlags warnings(Long schoolId, Long studentId);

    @Value
    @Builder
    class WarningFlags {
        boolean unpaidFees;
        boolean openLibraryLoans;
        boolean incompleteMarks;
    }
}
