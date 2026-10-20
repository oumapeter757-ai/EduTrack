package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.enums.SubscriptionTier;
import org.springframework.stereotype.Component;

import com.peterscode.edutrack.shared.exception.InvalidSubscriptionException;

@Component
public class PlanCatalog {
    public static int maxStudents(SubscriptionTier tier) {
        return switch (requireTier(tier)) {
            case FREE -> 100;
            case BASIC -> 300;
            case STANDARD -> 1000;
            case PREMIUM -> 3000;
            case ENTERPRISE -> throw new InvalidSubscriptionException("ENTERPRISE has no student limit");
        };
    }

    public static int maxTeachers(SubscriptionTier tier) {
        return switch (requireTier(tier)) {
            case FREE -> 10;
            case BASIC -> 30;
            case STANDARD -> 100;
            case PREMIUM -> 300;
            case ENTERPRISE -> throw new InvalidSubscriptionException("ENTERPRISE has no teacher limit");
        };
    }

    public static boolean unlimitedStudents(SubscriptionTier tier) {
        return requireTier(tier) == SubscriptionTier.ENTERPRISE;
    }

    public static boolean unlimitedTeachers(SubscriptionTier tier) {
        return requireTier(tier) == SubscriptionTier.ENTERPRISE;
    }

    private static SubscriptionTier requireTier(SubscriptionTier tier) {
        if (tier == null) {
            throw new InvalidSubscriptionException("Subscription tier is required");
        }
        return tier;
    }
}
