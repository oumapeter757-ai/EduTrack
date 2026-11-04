package com.peterscode.edutrack.payment.controller;

import com.peterscode.edutrack.payment.dto.MpesaCallbackRequest;
import com.peterscode.edutrack.payment.service.MpesaCallbackService;
import com.peterscode.edutrack.security.NoModuleRequired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@NoModuleRequired
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
public class MpesaWebhookController {

    private final MpesaCallbackService mpesaCallbackService;

    @Value("${mpesa.webhook-token:}")
    private String webhookToken;

    @PostMapping("/validation")
    @PreAuthorize("permitAll()")
    public ResponseEntity<MpesaCallbackRequest> handleValidation(
            @RequestParam(required = false) String token,
            @RequestBody MpesaCallbackRequest callback) {
        requireValidToken(token);
        log.info("Mpesa validation webhook received");
        MpesaCallbackRequest response = mpesaCallbackService.handleValidation(callback);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirmation")
    @PreAuthorize("permitAll()")
    public ResponseEntity<MpesaCallbackRequest> handleConfirmation(
            @RequestParam(required = false) String token,
            @RequestBody MpesaCallbackRequest callback) {
        requireValidToken(token);
        log.info("Mpesa confirmation webhook received");
        MpesaCallbackRequest response = mpesaCallbackService.handleConfirmation(callback);
        return ResponseEntity.ok(response);
    }

    private void requireValidToken(String suppliedToken) {
        if (webhookToken == null || webhookToken.length() < 32
                || suppliedToken == null
                || !MessageDigest.isEqual(
                        webhookToken.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment callback credentials");
        }
    }
}
