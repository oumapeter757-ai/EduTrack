package com.peterscode.edutrack.school.dto;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@JsonIgnoreProperties(ignoreUnknown = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchoolSubscriptionRequestDto {

    private SubscriptionTier subscriptionTier;

    private SubscriptionStatus subscriptionStatus;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;

    private Set<ServiceModule> enabledModules;

    @Positive(message = "must be greater than 0")
    private Integer maxStudents;

    @Positive(message = "must be greater than 0")
    private Integer maxTeachers;
}
