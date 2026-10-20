package com.peterscode.edutrack.security;

import com.peterscode.edutrack.school.enums.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

public record SchoolEntitlements(Long schoolId, SubscriptionTier tier, SubscriptionStatus status,
                                 LocalDate endDate, ZoneId timezone, Set<ServiceModule> enabledModules,
                                 Set<ServiceModule> tierModules, boolean active, long daysLeft) {
    public SchoolEntitlements {
        enabledModules = enabledModules == null ? Set.of() : Set.copyOf(enabledModules);
        tierModules = tierModules == null ? Set.of() : Set.copyOf(tierModules);
    }
    public boolean hasModule(ServiceModule module) {
        return enabledModules.contains(module) && tierModules.contains(module);
    }
}
