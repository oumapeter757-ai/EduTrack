package com.peterscode.edutrack.school.service;

import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.shared.exception.InvalidSubscriptionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTermsResolverTest {

    private final SubscriptionTermsResolver resolver = new SubscriptionTermsResolver();

    @Test
    void freeTierHasNoDates() {
        assertThat(resolver.resolve(SubscriptionTier.FREE, null, null, "UTC"))
                .isEqualTo(new SubscriptionTermsResolver.SubscriptionTerms(null, null));
    }

    @Test
    void freeTierRejectsDates() {
        assertThatThrownBy(() -> resolver.resolve(
                SubscriptionTier.FREE, LocalDate.now(), null, "UTC"))
                .isInstanceOf(InvalidSubscriptionException.class);
    }

    @Test
    void paidTierDefaultsStartToSchoolTimezoneToday() {
        LocalDate end = LocalDate.now().plusDays(10);

        assertThat(resolver.resolve(SubscriptionTier.STANDARD, null, end, "Africa/Nairobi").start())
                .isEqualTo(LocalDate.now(java.time.ZoneId.of("Africa/Nairobi")));
    }

    @Test
    void paidTierAllowsEndOnStartDate() {
        LocalDate start = LocalDate.now();

        assertThat(resolver.resolve(SubscriptionTier.STANDARD, start, start, "UTC").end())
                .isEqualTo(start);
    }

    @Test
    void invalidTimezoneIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(
                SubscriptionTier.STANDARD, null, LocalDate.now().plusDays(1), "invalid/timezone"))
                .isInstanceOf(InvalidSubscriptionException.class);
    }
}
