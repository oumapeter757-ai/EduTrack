package com.peterscode.edutrack.fees.controller;

import com.peterscode.edutrack.fees.dto.C2bCallbackRequest;
import com.peterscode.edutrack.fees.entity.SchoolPaymentConfig;
import com.peterscode.edutrack.fees.enums.CallbackChannel;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.service.C2bCallbackService;
import com.peterscode.edutrack.fees.service.SchoolPaymentConfigService;
import com.peterscode.edutrack.fees.service.StkCallbackService;
import com.peterscode.edutrack.security.NoModuleRequired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

/**
 * Public webhook controller for Mpesa C2B callbacks.
 * <p>
 * Endpoints are public (permitAll) and marked @NoModuleRequired because money
 * may already have moved — we can't reject callbacks based on subscription status.
 * The callback token in the path identifies the school.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fees/callback")
@RequiredArgsConstructor
@NoModuleRequired
public class C2bWebhookController {

    private final C2bCallbackService c2bCallbackService;
    private final SchoolPaymentConfigService configService;
    private final StkCallbackService stkCallbackService;

    /**
     * C2B Confirmation callback.
     * Safaricom hits this endpoint when a payment is confirmed.
     */
    @PostMapping("/c2b/confirmation/{token}")
    public ResponseEntity<Map<String, Object>> handleConfirmation(
            @PathVariable String token,
            @RequestBody C2bCallbackRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<SchoolPaymentConfig> configOpt = resolveSchoolByToken(token);
            if (configOpt.isEmpty()) {
                response.put("ResultCode", 1);
                response.put("ResultDesc", "School not found");
                return ResponseEntity.status(NOT_FOUND).body(response);
            }
            SchoolPaymentConfig config = configOpt.get();

            // Parse callback metadata
            C2bCallbackRequest.StkCallback callback = request.body().stkCallback();
            if (callback.resultCode() != null && callback.resultCode() != 0) {
                // Payment failed — just log it
                log.warn("C2B confirmation failed: {}", callback.resultDesc());
                response.put("ResultCode", 0);
                response.put("ResultDesc", "Acknowledged");
                return ResponseEntity.ok(response);
            }

            String providerReceipt = extractValue(callback, "MpesaReceiptNumber");
            BigDecimal amount = new BigDecimal(extractValue(callback, "Amount"));
            String accountReference = extractValue(callback, "BillNumber");
            String phoneNumber = extractValue(callback, "PhoneNumber");

            c2bCallbackService.processConfirmation(
                    config.getSchoolId(), CallbackChannel.C2B_CONFIRMATION,
                    providerReceipt, amount, accountReference, phoneNumber, Instant.now());

            response.put("ResultCode", 0);
            response.put("ResultDesc", "Accepted");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("C2B confirmation processing error", e);
            response.put("ResultCode", 0);
            response.put("ResultDesc", "Acknowledged for retry");
            return ResponseEntity.ok(response);
        }
    }

    /**
     * C2B Validation callback.
     * Safaricom hits this endpoint before confirming payment.
     */
    @PostMapping("/c2b/validation/{token}")
    public ResponseEntity<Map<String, Object>> handleValidation(
            @PathVariable String token,
            @RequestBody C2bCallbackRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<SchoolPaymentConfig> configOpt = resolveSchoolByToken(token);
            if (configOpt.isEmpty()) {
                response.put("ResultCode", 1);
                response.put("ResultDesc", "School not found");
                return ResponseEntity.status(NOT_FOUND).body(response);
            }
            SchoolPaymentConfig config = configOpt.get();

            C2bCallbackRequest.StkCallback callback = request.body().stkCallback();
            String providerReceipt = extractValue(callback, "BillNumber");
            BigDecimal amount = new BigDecimal(extractValue(callback, "Amount"));
            String accountReference = extractValue(callback, "AccountReference");
            String phoneNumber = extractValue(callback, "PhoneNumber");

            String result = c2bCallbackService.processValidation(
                    config.getSchoolId(), providerReceipt, amount,
                    accountReference, phoneNumber, Instant.now());

            response.put("ResultCode", "ACCEPT".equals(result) ? 0 : 1);
            response.put("ResultDesc", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("C2B validation processing error", e);
            response.put("ResultCode", 0);
            response.put("ResultDesc", "ACCEPT");
            return ResponseEntity.ok(response);
        }
    }

    private Optional<SchoolPaymentConfig> resolveSchoolByToken(String token) {
        // Hash the token the same way as SchoolPaymentConfigService does
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            String tokenHash = java.util.Base64.getEncoder().encodeToString(hash);
            return configService.getByCallbackToken(tokenHash);
        } catch (Exception e) {
            log.error("Token hashing failed", e);
            return Optional.empty();
        }
    }

    /**
     * STK Push result callback.
     * Safaricom hits this endpoint when an STK Push completes (success or failure).
     */
    @PostMapping("/stk/{token}")
    public ResponseEntity<Map<String, Object>> handleStkCallback(
            @PathVariable String token,
            @RequestBody C2bCallbackRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<SchoolPaymentConfig> configOpt = resolveSchoolByToken(token);
            if (configOpt.isEmpty()) {
                response.put("ResultCode", 1);
                response.put("ResultDesc", "School not found");
                return ResponseEntity.status(NOT_FOUND).body(response);
            }
            SchoolPaymentConfig config = configOpt.get();

            C2bCallbackRequest.StkCallback callback = request.body().stkCallback();
            String checkoutRequestID = callback.checkoutRequestID();
            int resultCode = callback.resultCode() != null ? callback.resultCode() : 1;
            String resultDesc = callback.resultDesc() != null ? callback.resultDesc() : "";

            BigDecimal amount = null;
            String mpesaReceipt = null;
            String phoneNumber = null;

            if (callback.callbackMetadata() != null && callback.callbackMetadata().item() != null) {
                amount = parseAmount(callback);
                mpesaReceipt = extractValue(callback, "MpesaReceiptNumber");
                phoneNumber = extractValue(callback, "PhoneNumber");
            }

            stkCallbackService.processStkResult(
                    config.getSchoolId(), checkoutRequestID,
                    resultCode, resultDesc, amount, phoneNumber,
                    mpesaReceipt, Instant.now());

            response.put("ResultCode", 0);
            response.put("ResultDesc", "Accepted");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("STK callback processing error", e);
            // Always acknowledge to prevent Safaricom retries
            response.put("ResultCode", 0);
            response.put("ResultDesc", "Acknowledged for retry");
            return ResponseEntity.ok(response);
        }
    }

    private String extractValue(C2bCallbackRequest.StkCallback callback, String name) {
        if (callback.callbackMetadata() == null || callback.callbackMetadata().item() == null) {
            return "";
        }
        return callback.callbackMetadata().item().stream()
                .filter(item -> name.equals(item.name()))
                .map(C2bCallbackRequest.CallbackItem::value)
                .findFirst()
                .orElse("");
    }

    private BigDecimal parseAmount(C2bCallbackRequest.StkCallback callback) {
        String value = extractValue(callback, "Amount");
        if (value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
