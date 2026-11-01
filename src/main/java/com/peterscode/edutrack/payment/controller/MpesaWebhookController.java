package com.peterscode.edutrack.payment.controller;

import com.peterscode.edutrack.payment.dto.MpesaCallbackRequest;
import com.peterscode.edutrack.payment.service.MpesaCallbackService;
import com.peterscode.edutrack.security.NoModuleRequired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@NoModuleRequired
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
public class MpesaWebhookController {

    private final MpesaCallbackService mpesaCallbackService;

    @PostMapping("/validation")
    @PreAuthorize("permitAll()")
    public ResponseEntity<MpesaCallbackRequest> handleValidation(
            @RequestBody MpesaCallbackRequest callback) {
        log.info("Mpesa validation webhook received");
        MpesaCallbackRequest response = mpesaCallbackService.handleValidation(callback);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirmation")
    @PreAuthorize("permitAll()")
    public ResponseEntity<MpesaCallbackRequest> handleConfirmation(
            @RequestBody MpesaCallbackRequest callback) {
        log.info("Mpesa confirmation webhook received");
        MpesaCallbackRequest response = mpesaCallbackService.handleConfirmation(callback);
        return ResponseEntity.ok(response);
    }
}
