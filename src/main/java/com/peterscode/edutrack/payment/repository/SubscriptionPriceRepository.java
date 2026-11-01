package com.peterscode.edutrack.payment.repository;

import com.peterscode.edutrack.payment.entity.SubscriptionPrice;
import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPriceRepository extends JpaRepository<SubscriptionPrice, Long> {

    Optional<SubscriptionPrice> findByTierAndBillingPeriodAndActiveTrue(SubscriptionTier tier, BillingPeriod billingPeriod);

    List<SubscriptionPrice> findByActiveTrue();
}
