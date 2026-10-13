package com.peterscode.edutrack.school.dto;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

/**
 * Super Admin DTO for managing school subscription tier, status, module entitlements, and capacity limits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchoolSubscriptionRequestDto {

    @NotNull(message = "Subscription tier is required")
    private SubscriptionTier subscriptionTier;

    @NotNull(message = "Subscription status is required")
    private SubscriptionStatus subscriptionStatus;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;

    private Set<ServiceModule> enabledModules;

    private Integer maxStudents;

    private Integer maxTeachers;
}
