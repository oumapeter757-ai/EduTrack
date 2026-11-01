package com.peterscode.edutrack.payment.entity;

import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "subscription_prices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_price_tier_period", columnNames = {"tier", "billingPeriod"})
})
public class SubscriptionPrice extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;
}
