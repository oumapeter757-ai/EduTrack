package com.peterscode.edutrack.payment.controller;

import com.peterscode.edutrack.payment.dto.CreateSubscriptionPriceRequestDto;
import com.peterscode.edutrack.payment.dto.SubscriptionPriceDto;
import com.peterscode.edutrack.payment.service.SubscriptionPriceService;
import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/subscription-prices")
@RequiredArgsConstructor
public class SubscriptionPriceController {

    private final SubscriptionPriceService subscriptionPriceService;

    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<SubscriptionPriceDto>>> getActivePrices() {
        List<SubscriptionPriceDto> prices = subscriptionPriceService.getActivePrices();
        return ResponseEntity.ok(ApiResponse.success("Active subscription prices retrieved", prices));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPriceDto>> createPrice(
            @Valid @RequestBody CreateSubscriptionPriceRequestDto requestDto) {
        SubscriptionPriceDto price = subscriptionPriceService.createPrice(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subscription price created", price));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPriceDto>> updatePrice(
            @PathVariable Long id,
            @Valid @RequestBody CreateSubscriptionPriceRequestDto requestDto) {
        SubscriptionPriceDto price = subscriptionPriceService.updatePrice(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Subscription price updated", price));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPriceDto>> deactivatePrice(@PathVariable Long id) {
        SubscriptionPriceDto price = subscriptionPriceService.deactivatePrice(id);
        return ResponseEntity.ok(ApiResponse.success("Subscription price deactivated", price));
    }
}
