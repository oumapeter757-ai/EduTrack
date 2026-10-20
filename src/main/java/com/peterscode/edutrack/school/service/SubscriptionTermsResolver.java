package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.shared.exception.InvalidSubscriptionException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Resolves and validates subscription term dates (start/end) per tier.
 *
 * <p><b>End-date semantics (project-wide contract):</b> {@code subscriptionEndDate} is
 * <b>INCLUSIVE</b> — the subscription is still ACTIVE on the end date itself, and only
 * transitions to GRACE/EXPIRED on the day <i>after</i> the end date in the school's timezone.
 * All date comparisons across the codebase (ModuleAccessService#effectiveStatus,
 * SchoolEntitlements#daysLeft, expiry scheduler) must follow this rule.
 */
@Component
public class SubscriptionTermsResolver {
    private final Clock clock;

    public SubscriptionTermsResolver() {
        this(Clock.systemUTC());
    }

    public SubscriptionTermsResolver(Clock clock) {
        this.clock = clock;
    }

    public SubscriptionTerms resolve(
            SubscriptionTier tier,
            LocalDate requestedStart,
            LocalDate requestedEnd,
            String timezone) {
        return resolve(tier, requestedStart, requestedEnd, timezone, null, null, false);
    }

    public SubscriptionTerms resolve(SubscriptionTier tier, LocalDate requestedStart, LocalDate requestedEnd,
                                     String timezone, LocalDate existingStart, LocalDate existingEnd,
                                     boolean allowPastEnd) {
        if (tier == null) {
            throw new InvalidSubscriptionException("Subscription tier is required");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new InvalidSubscriptionException("Invalid school timezone");
        }

        if (tier == SubscriptionTier.FREE) {
            if (requestedStart != null || requestedEnd != null) {
                throw new InvalidSubscriptionException("FREE subscriptions cannot have subscription dates");
            }
            return new SubscriptionTerms(null, null);
        }

        LocalDate start = requestedStart != null ? requestedStart
                : existingStart != null ? existingStart : LocalDate.now(clock.withZone(zoneId));
        if (requestedEnd == null) {
            if (existingEnd != null) requestedEnd = existingEnd;
            else throw new InvalidSubscriptionException("Subscription end date is required for paid tiers");
        }
        if (requestedEnd.isBefore(start)) {
            throw new InvalidSubscriptionException("Subscription end date must not be before the start date");
        }
        if (!allowPastEnd && requestedEnd.isBefore(LocalDate.now(clock.withZone(zoneId)))) {
            throw new InvalidSubscriptionException("Subscription end date cannot be in the past");
        }
        if (ChronoUnit.YEARS.between(start, requestedEnd) > 5) {
            throw new InvalidSubscriptionException("Subscription term cannot exceed five years");
        }

        return new SubscriptionTerms(start, requestedEnd);
    }

    public record SubscriptionTerms(LocalDate start, LocalDate end) {
    }
}
