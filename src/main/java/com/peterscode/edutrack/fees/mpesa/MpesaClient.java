package com.peterscode.edutrack.fees.mpesa;

import java.math.BigDecimal;

/**
 * Interface for Mpesa Daraja API interactions.
 * <p>
 * Implemented by DarajaMpesaClient (production) and FakeMpesaClient (tests).
 * Tests never call a real network — they use FakeMpesaClient.
 * </p>
 */
public interface MpesaClient {

    /**
     * Initiate an STK Push request.
     *
     * @param phoneNumber  12-digit Kenyan phone number (e.g., 254712345678)
     * @param amount       payment amount in KES
     * @param callbackUrl  URL for STK result callback
     * @param accountReference  used to match the callback (e.g., CheckoutRequestID)
     * @param description  text shown to the user on the phone prompt
     * @return StkPushResult containing the CheckoutRequestID
     */
    StkPushResult initiateStkPush(String phoneNumber, BigDecimal amount,
                                   String callbackUrl, String accountReference,
                                   String description);

    /**
     * Query the status of an STK Push by its CheckoutRequestID.
     * Used by the timeout job to resolve stale PENDING payments.
     *
     * @param checkoutRequestID the ID returned by initiateStkPush
     * @return StkPushStatusResult with the current status
     */
    StkPushStatusResult queryStkPushStatus(String checkoutRequestID);

    /**
     * Result of an STK Push initiation.
     */
    record StkPushResult(
            String checkoutRequestID,
            String customerMessage,
            boolean success
    ) {}

    /**
     * Result of an STK Push status query.
     */
    record StkPushStatusResult(
            String checkoutRequestID,
            String resultCode,
            String resultDesc,
            boolean completed
    ) {}
}
