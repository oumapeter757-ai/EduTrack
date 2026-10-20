package com.peterscode.edutrack.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.*;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleAccessService {
    private final SchoolRepository schools;
    private final SecurityUtils security;
    private final Clock clock;
    private final Cache<Long, SchoolEntitlements> cache = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(30, TimeUnit.SECONDS).build();
    @Value("${edutrack.subscription.grace-days:14}") private int graceDays;

    public SchoolEntitlements entitlements(Long schoolId) {
        if (schoolId == null) throw new ModuleAccessDeniedException(null, currentUser(), null, "SCHOOL_REQUIRED", "School context is required");
        return cache.get(schoolId, this::load);
    }

    private SchoolEntitlements load(Long id) {
        School s = schools.findById(id).orElseThrow(() ->
                new ModuleAccessDeniedException(id, currentUser(), null, "SCHOOL_NOT_FOUND", "School not found"));
        ZoneId zone;
        try {
            zone = ZoneId.of(s.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            throw new ModuleAccessDeniedException(id, currentUser(), null,
                    "ACCESS_DENIED", "Subscription access is unavailable");
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        SubscriptionStatus effectiveStatus = effectiveStatus(s, today);
        long daysLeft = computeDaysLeft(s, effectiveStatus, today);
        return new SchoolEntitlements(id, s.getSubscriptionTier(), effectiveStatus,
                s.getSubscriptionEndDate(), zone, s.getEnabledModules(),
                ServiceModule.getDefaultModulesForTier(s.getSubscriptionTier()), s.isActive(), daysLeft);
    }

    /**
     * Derives the effective subscription status from stored status + dates, respecting the
     * project-wide INCLUSIVE end-date rule (SubscriptionTermsResolver Javadoc).
     *
     * <p>Preserves SUSPENDED and CANCELLED. Preserves TRIAL until its end date (then EXPIRED).
     * Warns on a null stored status for a non-FREE school (treated as EXPIRED).
     */
    private SubscriptionStatus effectiveStatus(School s, LocalDate today) {
        if (!s.isActive() || s.getSubscriptionStatus() == SubscriptionStatus.SUSPENDED ||
                s.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) return s.getSubscriptionStatus();
        if (s.getSubscriptionTier() == SubscriptionTier.FREE && s.getSubscriptionEndDate() == null) {
            return SubscriptionStatus.ACTIVE;
        }
        LocalDate end = s.getSubscriptionEndDate();
        SubscriptionStatus stored = s.getSubscriptionStatus();
        if (stored == null && s.getSubscriptionTier() != SubscriptionTier.FREE) {
            log.warn("School id={} has non-FREE tier={} but a null stored subscription status; treating as EXPIRED",
                    s.getId(), s.getSubscriptionTier());
        }
        if (end == null) return SubscriptionStatus.EXPIRED;
        if (stored == SubscriptionStatus.TRIAL) {
            // Preserve TRIAL until its end date; after that fall through to EXPIRED (no GRACE for trials).
            return today.isAfter(end) ? SubscriptionStatus.EXPIRED : SubscriptionStatus.TRIAL;
        }
        if (!today.isAfter(end)) return SubscriptionStatus.ACTIVE;
        if (!today.isAfter(end.plusDays(Math.max(0, graceDays)))) return SubscriptionStatus.GRACE;
        return SubscriptionStatus.EXPIRED;
    }

    /**
     * Days remaining on the subscription, inclusive of the end date and in the school's timezone.
     * - ACTIVE / TRIAL: days until end date (>= 0).
     * - GRACE: days until end-of-grace window.
     * - Otherwise: 0.
     */
    private long computeDaysLeft(School s, SubscriptionStatus effective, LocalDate today) {
        LocalDate end = s.getSubscriptionEndDate();
        if (end == null) return 0;
        return switch (effective) {
            case ACTIVE, TRIAL -> Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, end));
            case GRACE -> Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(
                    today, end.plusDays(Math.max(0, graceDays))));
            default -> 0L;
        };
    }

    public void evict(Long schoolId) { if (schoolId != null) cache.invalidate(schoolId); }
    public boolean isSchoolActive(Long schoolId) {
        if (schoolId == null) return false;
        try {
            return cache.get(schoolId, this::load).active();
        } catch (RuntimeException ex) {
            log.debug("Unable to resolve school activity for {}", schoolId);
            return false;
        }
    }
    public void assertModuleEnabled(Long schoolId, com.peterscode.edutrack.school.enums.ServiceModule module) {
        if (schoolId == null) {
            throw new ModuleAccessDeniedException(null, currentUser(), module == null ? null : module.name(),
                    "SCHOOL_REQUIRED", "School context is required");
        }
        if (module == null) {
            return;
        }
        SchoolEntitlements e = entitlements(schoolId);
        if (!e.active()) {
            throw new ModuleAccessDeniedException(schoolId, currentUser(), module.name(),
                    "SCHOOL_INACTIVE", "School is inactive");
        }
        SubscriptionStatus st = e.status();
        if (st == SubscriptionStatus.SUSPENDED || st == SubscriptionStatus.CANCELLED) {
            throw new ModuleAccessDeniedException(schoolId, currentUser(), module.name(),
                    "ACCESS_DENIED", "Subscription is " + st.name().toLowerCase());
        }
        if (!e.hasModule(module)) {
            throw new ModuleAccessDeniedException(schoolId, currentUser(), module.name(),
                    "MODULE_NOT_IN_PLAN", "Module is not entitled for school plan");
        }
    }

    public void clear() { cache.invalidateAll(); }
    public long estimatedSize() { return cache.estimatedSize(); }
    private Long currentUser() { var p = security.getCurrentPrincipal(); return p == null ? null : p.getId(); }
}
