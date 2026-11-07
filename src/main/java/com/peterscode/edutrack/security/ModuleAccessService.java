package com.peterscode.edutrack.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.*;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleAccessService {
    private final SchoolRepository schools;
    private final SecurityUtils security;
    private final Clock clock;
    private final Cache<Long, EntitlementSnapshot> cache = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(30, TimeUnit.SECONDS).build();
    @Value("${edutrack.subscription.grace-days:14}") private int graceDays;

    @PostConstruct
    void validateGraceDays() {
        if (graceDays < 0) {
            throw new IllegalStateException("Subscription grace days must be non-negative");
        }
    }

    public SchoolEntitlements entitlements(Long schoolId) {
        if (schoolId == null) {
            throw denied(null, null, "ACCESS_DENIED", "School context is required");
        }
        EntitlementSnapshot snapshot = cache.get(schoolId, this::load);
        LocalDate today = LocalDate.now(clock.withZone(snapshot.timezone()));
        SubscriptionStatus status = effectiveStatus(snapshot, today);
        long daysLeft = computeDaysLeft(snapshot, status, today);
        return new SchoolEntitlements(snapshot.schoolId(), snapshot.tier(), status, snapshot.endDate(),
                snapshot.timezone(), snapshot.enabledModules(),
                ServiceModule.getDefaultModulesForTier(snapshot.tier()), snapshot.active(), daysLeft,
                snapshot.storedStatus());
    }

    private EntitlementSnapshot load(Long id) {
        School s = schools.findByIdWithModules(id).orElseThrow(() ->
                denied(id, null, "ACCESS_DENIED", "School access is unavailable"));
        ZoneId zone;
        try {
            zone = ZoneId.of(s.getTimezone());
        } catch (DateTimeException | NullPointerException exception) {
            throw denied(id, null, "ACCESS_DENIED", "Subscription access is unavailable");
        }
        if (s.getSubscriptionTier() == null) {
            throw denied(id, null, "ACCESS_DENIED", "Subscription access is unavailable");
        }
        return new EntitlementSnapshot(id, s.getSubscriptionTier(), s.getSubscriptionStatus(),
                s.getSubscriptionStartDate(), s.getSubscriptionEndDate(), zone,
                s.getEnabledModules(), s.isActive());
    }

    private SubscriptionStatus effectiveStatus(EntitlementSnapshot snapshot, LocalDate today) {
        SubscriptionStatus stored = snapshot.storedStatus();
        if (stored == null) {
            return null;
        }
        if (stored == SubscriptionStatus.SUSPENDED || stored == SubscriptionStatus.CANCELLED) {
            return stored;
        }
        if (snapshot.tier() == SubscriptionTier.FREE) {
            return SubscriptionStatus.ACTIVE;
        }
        if (stored == SubscriptionStatus.EXPIRED || snapshot.endDate() == null) {
            return SubscriptionStatus.EXPIRED;
        }
        LocalDate end = snapshot.endDate();
        if (stored == SubscriptionStatus.TRIAL) {
            return today.isAfter(end) ? SubscriptionStatus.EXPIRED : SubscriptionStatus.TRIAL;
        }
        if (!today.isAfter(end)) {
            return SubscriptionStatus.ACTIVE;
        }
        if (!today.isAfter(end.plusDays(graceDays))) {
            return SubscriptionStatus.GRACE;
        }
        return SubscriptionStatus.EXPIRED;
    }

    private long computeDaysLeft(EntitlementSnapshot snapshot, SubscriptionStatus effective, LocalDate today) {
        LocalDate end = snapshot.endDate();
        if (end == null || effective == null) {
            return 0;
        }
        return switch (effective) {
            case ACTIVE, TRIAL -> Math.max(0, ChronoUnit.DAYS.between(today, end));
            case GRACE -> Math.max(0, ChronoUnit.DAYS.between(today, end.plusDays(graceDays)));
            default -> 0L;
        };
    }

    public void evict(Long schoolId) { if (schoolId != null) cache.invalidate(schoolId); }
    public boolean isSchoolActive(Long schoolId) {
        if (schoolId == null) return false;
        try {
            return entitlements(schoolId).active();
        } catch (RuntimeException ex) {
            log.debug("Unable to resolve school activity for {}", schoolId);
            return false;
        }
    }

    public void assertModuleEnabled(Long schoolId, ServiceModule module) {
        if (schoolId == null) {
            throw denied(null, module, "ACCESS_DENIED", "School context is required");
        }
        if (module == null) {
            throw denied(schoolId, null, "ACCESS_DENIED", "Subscription access is unavailable");
        }
        SchoolEntitlements e = entitlements(schoolId);
        if (!e.active()) {
            throw denied(schoolId, module, "SCHOOL_INACTIVE", "School is inactive");
        }
        SubscriptionStatus st = e.status();
        if (st == SubscriptionStatus.EXPIRED) {
            throw denied(schoolId, module, "SUBSCRIPTION_EXPIRED", "Subscription has expired");
        }
        if (st == null || st == SubscriptionStatus.SUSPENDED || st == SubscriptionStatus.CANCELLED) {
            throw denied(schoolId, module, "ACCESS_DENIED", "Subscription access is denied");
        }
        if (!e.hasModule(module)) {
            throw denied(schoolId, module, "MODULE_NOT_IN_PLAN", "Module is not enabled");
        }
    }

    public boolean isModuleEnabled(Long schoolId, ServiceModule module) {
        try {
            assertModuleEnabled(schoolId, module);
            return true;
        } catch (ModuleAccessDeniedException ex) {
            return false;
        }
    }

    public void clear() { cache.invalidateAll(); }
    public long estimatedSize() { return cache.estimatedSize(); }
    private Long currentUser() { var p = security.getCurrentPrincipal(); return p == null ? null : p.getId(); }

    private ModuleAccessDeniedException denied(Long schoolId, ServiceModule module, String code, String message) {
        return new ModuleAccessDeniedException(schoolId, currentUser(), module == null ? null : module.name(), code, message);
    }

    private record EntitlementSnapshot(Long schoolId, SubscriptionTier tier, SubscriptionStatus storedStatus,
            LocalDate startDate, LocalDate endDate, ZoneId timezone,
            java.util.Set<ServiceModule> enabledModules, boolean active) {
        private EntitlementSnapshot {
            enabledModules = enabledModules == null ? java.util.Set.of() : java.util.Set.copyOf(enabledModules);
        }
    }
}
