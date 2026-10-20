package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.security.SchoolEntitlements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TierAndModulePolicyTest {

    @Test
    @DisplayName("Tier modules must be strictly cumulative: FREE ⊂ BASIC ⊂ STANDARD ⊂ PREMIUM ⊂ ENTERPRISE")
    void testTierModulesCumulativePolicy() {
        Set<ServiceModule> free = ServiceModule.getDefaultModulesForTier(SubscriptionTier.FREE);
        Set<ServiceModule> basic = ServiceModule.getDefaultModulesForTier(SubscriptionTier.BASIC);
        Set<ServiceModule> standard = ServiceModule.getDefaultModulesForTier(SubscriptionTier.STANDARD);
        Set<ServiceModule> premium = ServiceModule.getDefaultModulesForTier(SubscriptionTier.PREMIUM);
        Set<ServiceModule> enterprise = ServiceModule.getDefaultModulesForTier(SubscriptionTier.ENTERPRISE);

        assertThat(basic).containsAll(free);
        assertThat(standard).containsAll(basic);
        assertThat(premium).containsAll(standard);
        assertThat(enterprise).containsAll(premium);

        assertThat(enterprise).containsExactlyInAnyOrder(ServiceModule.values());
    }

    @Test
    @DisplayName("Every tier bundle must contain all prerequisites of each module included in it")
    void testTierBundlesContainModulePrerequisites() {
        Map<ServiceModule, Set<ServiceModule>> dependencyMap = ServiceModule.getDependencies();

        for (SubscriptionTier tier : SubscriptionTier.values()) {
            Set<ServiceModule> tierModules = ServiceModule.getDefaultModulesForTier(tier);

            for (ServiceModule module : tierModules) {
                Set<ServiceModule> requiredPrereqs = dependencyMap.get(module);
                if (requiredPrereqs != null) {
                    assertThat(tierModules)
                            .as("Tier %s bundle must contain prerequisites for module %s", tier, module)
                            .containsAll(requiredPrereqs);
                }
            }
        }
    }

    @Test
    @DisplayName("Effective access is the intersection of enabledModules and tierModules")
    void testEffectiveAccessIntersectionEnforcement() {
        Set<ServiceModule> tierModules = ServiceModule.getDefaultModulesForTier(SubscriptionTier.FREE);
        // Suppose a row was tampered or stale and includes BILLING (which FREE does not entitle)
        Set<ServiceModule> tamperedEnabled = Set.of(
                ServiceModule.STUDENT_MANAGEMENT,
                ServiceModule.BILLING // NOT in FREE!
        );

        SchoolEntitlements entitlements = new SchoolEntitlements(
                1L,
                SubscriptionTier.FREE,
                SubscriptionStatus.ACTIVE,
                null,
                ZoneId.of("UTC"),
                tamperedEnabled,
                tierModules,
                true,
                0
        );

        // STUDENT_MANAGEMENT is in both enabledModules AND tierModules -> allowed
        assertThat(entitlements.hasModule(ServiceModule.STUDENT_MANAGEMENT)).isTrue();

        // BILLING is in enabledModules but NOT in tierModules -> DENIED
        assertThat(entitlements.hasModule(ServiceModule.BILLING)).isFalse();

        // ATTENDANCE is in tierModules but NOT in enabledModules -> DENIED
        assertThat(entitlements.hasModule(ServiceModule.ATTENDANCE)).isFalse();
    }
}
