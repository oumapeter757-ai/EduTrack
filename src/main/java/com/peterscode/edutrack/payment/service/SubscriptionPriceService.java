package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.dto.CreateSubscriptionPriceRequestDto;
import com.peterscode.edutrack.payment.dto.SubscriptionPriceDto;
import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.school.enums.SubscriptionTier;

import java.util.List;

public interface SubscriptionPriceService {

    List<SubscriptionPriceDto> getActivePrices();

    SubscriptionPriceDto getPrice(SubscriptionTier tier, BillingPeriod billingPeriod);

    SubscriptionPriceDto createPrice(CreateSubscriptionPriceRequestDto requestDto);

    SubscriptionPriceDto updatePrice(Long id, CreateSubscriptionPriceRequestDto requestDto);

    SubscriptionPriceDto deactivatePrice(Long id);
}
