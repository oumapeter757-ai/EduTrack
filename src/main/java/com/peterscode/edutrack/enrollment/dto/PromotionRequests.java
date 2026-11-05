package com.peterscode.edutrack.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.peterscode.edutrack.enrollment.entity.PromotionOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PromotionRequests {
    private PromotionRequests() {}
    @Data
    public static class CreateRun {
        @NotNull private Long schoolId;
        @NotNull private Long fromAcademicYearId;
        @NotNull private Long toAcademicYearId;
    }
    @Data
    public static class OverrideBatch {
        @NotEmpty @Size(max = 500) @Valid private List<OverrideItem> items;
    }
    @Data
    public static class OverrideItem {
        @NotNull private Long decisionId;
        @NotNull private PromotionOutcome outcome;
        private Long targetClassLevelId;
        private Long targetStreamId;
        @NotNull @Size(min = 1, max = 500) private String reason;
    }
}
