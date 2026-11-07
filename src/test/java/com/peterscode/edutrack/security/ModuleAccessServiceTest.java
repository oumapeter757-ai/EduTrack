package com.peterscode.edutrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModuleAccessServiceTest {
    @Mock private SchoolRepository schools;
    @Mock private SecurityUtils security;

    private MutableClock clock;
    private ModuleAccessService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
        service = new ModuleAccessService(schools, security, clock);
        ReflectionTestUtils.setField(service, "graceDays", 14);
    }

    @Test
    void effectiveStatusIsRecomputedFromCachedSnapshotOnEveryRequest() {
        School school = school(SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE,
                LocalDate.of(2026, 1, 15), EnumSet.allOf(ServiceModule.class));
        when(schools.findByIdWithModules(7L)).thenReturn(Optional.of(school));

        assertThat(service.entitlements(7L).status()).isEqualTo(SubscriptionStatus.ACTIVE);
        clock.setInstant(Instant.parse("2026-01-16T00:01:00Z"));
        assertThat(service.entitlements(7L).status()).isEqualTo(SubscriptionStatus.GRACE);
        clock.setInstant(Instant.parse("2026-01-30T00:01:00Z"));
        assertThat(service.entitlements(7L).status()).isEqualTo(SubscriptionStatus.EXPIRED);

        verify(schools, times(1)).findByIdWithModules(7L);
    }

    @Test
    void expiryDateIsEvaluatedInTheSchoolsTimezone() {
        School school = school(SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE,
                LocalDate.of(2026, 1, 15), EnumSet.allOf(ServiceModule.class));
        school.setTimezone("America/Los_Angeles");
        when(schools.findByIdWithModules(7L)).thenReturn(Optional.of(school));
        clock.setInstant(Instant.parse("2026-01-16T00:01:00Z"));

        assertThat(service.entitlements(7L).status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void entitlementsExposeStoredAndEffectiveStatusesSeparately() {
        School school = school(SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE,
                LocalDate.of(2026, 1, 14), EnumSet.allOf(ServiceModule.class));
        when(schools.findByIdWithModules(7L)).thenReturn(Optional.of(school));

        SchoolEntitlements entitlements = service.entitlements(7L);

        assertThat(entitlements.storedStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(entitlements.status()).isEqualTo(SubscriptionStatus.GRACE);
    }

    @Test
    void freeTierCannotGainTimetableThroughTamperedEnabledModules() {
        School school = school(SubscriptionTier.FREE, SubscriptionStatus.ACTIVE, null,
                EnumSet.allOf(ServiceModule.class));
        when(schools.findByIdWithModules(7L)).thenReturn(Optional.of(school));

        SchoolEntitlements entitlements = service.entitlements(7L);

        assertThat(entitlements.hasModule(ServiceModule.ATTENDANCE)).isTrue();
        assertThat(entitlements.hasModule(ServiceModule.TIMETABLE)).isFalse();
    }

    @Test
    void cachedEntitlementsAreIsolatedBySchoolId() {
        School freeSchool = school(7L, SubscriptionTier.FREE, SubscriptionStatus.ACTIVE, null,
                EnumSet.allOf(ServiceModule.class));
        School basicSchool = school(8L, SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE, null,
                EnumSet.allOf(ServiceModule.class));
        when(schools.findByIdWithModules(7L)).thenReturn(Optional.of(freeSchool));
        when(schools.findByIdWithModules(8L)).thenReturn(Optional.of(basicSchool));

        assertThat(service.entitlements(7L).hasModule(ServiceModule.TIMETABLE)).isFalse();
        assertThat(service.entitlements(8L).hasModule(ServiceModule.TIMETABLE)).isTrue();
        assertThat(service.entitlements(7L).hasModule(ServiceModule.TIMETABLE)).isFalse();

        verify(schools, times(1)).findByIdWithModules(7L);
        verify(schools, times(1)).findByIdWithModules(8L);
    }

    @Test
    void cacheEvictionMakesTierChangesVisibleOnTheNextRequest() {
        School freeSchool = school(7L, SubscriptionTier.FREE, SubscriptionStatus.ACTIVE, null,
                EnumSet.allOf(ServiceModule.class));
        School basicSchool = school(7L, SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE, null,
                EnumSet.allOf(ServiceModule.class));
        when(schools.findByIdWithModules(7L)).thenReturn(Optional.of(freeSchool), Optional.of(basicSchool), Optional.of(freeSchool));

        assertThat(service.entitlements(7L).hasModule(ServiceModule.TIMETABLE)).isFalse();
        service.evict(7L);
        assertThat(service.entitlements(7L).hasModule(ServiceModule.TIMETABLE)).isTrue();
        service.evict(7L);
        assertThat(service.entitlements(7L).hasModule(ServiceModule.TIMETABLE)).isFalse();
    }

    @Test
    void backgroundAccessFailsClosedAfterSubscriptionGrace() {
        School school = school(SubscriptionTier.BASIC, SubscriptionStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), EnumSet.allOf(ServiceModule.class));
        when(schools.findByIdWithModules(7L)).thenReturn(Optional.of(school));
        clock.setInstant(Instant.parse("2026-01-16T00:01:00Z"));

        assertThatThrownBy(() -> service.assertModuleEnabled(7L, ServiceModule.TIMETABLE))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SUBSCRIPTION_EXPIRED");
    }

    @Test
    void missingSchoolFailsClosed() {
        when(schools.findByIdWithModules(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entitlements(404L))
                .isInstanceOf(ModuleAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACCESS_DENIED");
    }

    @Test
    void negativeGracePeriodFailsStartupValidation() {
        ReflectionTestUtils.setField(service, "graceDays", -1);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateGraceDays"))
                .isInstanceOf(IllegalStateException.class);
    }

    private School school(SubscriptionTier tier, SubscriptionStatus status, LocalDate endDate,
            java.util.Set<ServiceModule> modules) {
        return school(7L, tier, status, endDate, modules);
    }

    private School school(Long id, SubscriptionTier tier, SubscriptionStatus status, LocalDate endDate,
            java.util.Set<ServiceModule> modules) {
        return School.builder().id(id).active(true).timezone("UTC").subscriptionTier(tier)
                .subscriptionStatus(status).subscriptionEndDate(endDate).enabledModules(modules).build();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
