package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.dto.CreateSubscriptionPriceRequestDto;
import com.peterscode.edutrack.payment.dto.SubscriptionPriceDto;
import com.peterscode.edutrack.payment.entity.SubscriptionPrice;
import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.payment.repository.SubscriptionPriceRepository;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPriceServiceImpl implements SubscriptionPriceService {

    private final SubscriptionPriceRepository subscriptionPriceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPriceDto> getActivePrices() {
        return subscriptionPriceRepository.findByActiveTrue().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPriceDto getPrice(SubscriptionTier tier, BillingPeriod billingPeriod) {
        return subscriptionPriceRepository.findByTierAndBillingPeriodAndActiveTrue(tier, billingPeriod)
                .map(this::toDto)
                .orElseThrow(() -> new com.peterscode.edutrack.shared.exception.ResourceNotFoundException(
                        "No active price found for tier " + tier + " and period " + billingPeriod));
    }

    @Override
    @Transactional
    public SubscriptionPriceDto createPrice(CreateSubscriptionPriceRequestDto requestDto) {
        SubscriptionPrice price = SubscriptionPrice.builder()
                .tier(requestDto.getTier())
                .billingPeriod(requestDto.getBillingPeriod())
                .amount(requestDto.getAmount())
                .currency(requestDto.getCurrency() != null ? requestDto.getCurrency() : "KES")
                .active(true)
                .effectiveFrom(requestDto.getEffectiveFrom())
                .build();
        price = subscriptionPriceRepository.save(price);
        log.info("Created subscription price for tier {} period {}: {}", price.getTier(), price.getBillingPeriod(), price.getAmount());
        return toDto(price);
    }

    @Override
    @Transactional
    public SubscriptionPriceDto updatePrice(Long id, CreateSubscriptionPriceRequestDto requestDto) {
        SubscriptionPrice price = subscriptionPriceRepository.findById(id)
                .orElseThrow(() -> new com.peterscode.edutrack.shared.exception.ResourceNotFoundException(
                        "Subscription price not found with id " + id));
        if (requestDto.getAmount() != null) {
            price.setAmount(requestDto.getAmount());
        }
        if (requestDto.getCurrency() != null) {
            price.setCurrency(requestDto.getCurrency());
        }
        if (requestDto.getEffectiveFrom() != null) {
            price.setEffectiveFrom(requestDto.getEffectiveFrom());
        }
        price = subscriptionPriceRepository.save(price);
        log.info("Updated subscription price ID {}: {}", id, price.getAmount());
        return toDto(price);
    }

    @Override
    @Transactional
    public SubscriptionPriceDto deactivatePrice(Long id) {
        SubscriptionPrice price = subscriptionPriceRepository.findById(id)
                .orElseThrow(() -> new com.peterscode.edutrack.shared.exception.ResourceNotFoundException(
                        "Subscription price not found with id " + id));
        price.setActive(false);
        price = subscriptionPriceRepository.save(price);
        log.info("Deactivated subscription price ID {}", id);
        return toDto(price);
    }

    private SubscriptionPriceDto toDto(SubscriptionPrice price) {
        return SubscriptionPriceDto.builder()
                .id(price.getId())
                .tier(price.getTier())
                .billingPeriod(price.getBillingPeriod())
                .amount(price.getAmount())
                .currency(price.getCurrency())
                .active(price.isActive())
                .effectiveFrom(price.getEffectiveFrom())
                .createdAt(price.getCreatedAt())
                .updatedAt(price.getUpdatedAt())
                .build();
    }
}
