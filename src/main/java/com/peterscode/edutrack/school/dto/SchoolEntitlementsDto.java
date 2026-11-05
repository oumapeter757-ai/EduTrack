package com.peterscode.edutrack.school.dto;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

/**
 * Response DTO for school subscription entitlements.
 * Exposes tier, status, effective status, end date, days left, and enabled modules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolEntitlementsDto {
    private SubscriptionTier tier;
    private SubscriptionStatus storedStatus;
    private SubscriptionStatus effectiveStatus;
    private LocalDate endDate;
    private long daysLeft;
    private Set<ServiceModule> enabledModules;
    private Set<ServiceModule> tierModules;
    private boolean active;
}
