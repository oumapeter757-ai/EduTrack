package com.peterscode.edutrack.fees.mpesa;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake implementation of MpesaClient for unit tests.
 * <p>
 * Never calls the real Safaricom network. Simulates STK Push behavior
 * with configurable outcomes.
 * </p>
 */
public class FakeMpesaClient implements MpesaClient {

    /**
     * Simulated outcomes per CheckoutRequestID.
     * Tests can configure this to simulate success, failure, or timeout.
     */
    private final Map<String, SimulatedOutcome> outcomes = new ConcurrentHashMap<>();

    /**
     * Default outcome when no specific outcome is configured.
     */
    private SimulatedOutcome defaultOutcome = SimulatedOutcome.SUCCESS;

    @Override
    public StkPushResult initiateStkPush(String phoneNumber, BigDecimal amount,
                                          String callbackUrl, String accountReference,
                                          String description) {
        // Simulate successful initiation — the real result comes via callback
        String checkoutRequestID = "CHK-" + System.currentTimeMillis();
        outcomes.put(checkoutRequestID, defaultOutcome);
        return new StkPushResult(checkoutRequestID, "Success. Please enter your M-PESA PIN.", true);
    }

    @Override
    public StkPushStatusResult queryStkPushStatus(String checkoutRequestID) {
        SimulatedOutcome outcome = outcomes.getOrDefault(checkoutRequestID, defaultOutcome);
        return outcome.toStatusResult(checkoutRequestID);
    }

    /**
     * Configure the outcome for a specific CheckoutRequestID.
     */
    public void setOutcome(String checkoutRequestID, SimulatedOutcome outcome) {
        outcomes.put(checkoutRequestID, outcome);
    }

    /**
     * Set the default outcome for all new requests.
     */
    public void setDefaultOutcome(SimulatedOutcome outcome) {
        this.defaultOutcome = outcome;
    }

    /**
     * Simulated STK Push outcomes.
     */
    public enum SimulatedOutcome {
        SUCCESS("0", "The service request is processed successfully."),
        FAILED("1", "The service request is failed."),
        CANCELLED("2", "Customer cancelled the transaction."),
        TIMEOUT("3", "The service request timed out.");

        private final String resultCode;
        private final String resultDesc;

        SimulatedOutcome(String resultCode, String resultDesc) {
            this.resultCode = resultCode;
            this.resultDesc = resultDesc;
        }

        public StkPushStatusResult toStatusResult(String checkoutRequestID) {
            return new StkPushStatusResult(
                    checkoutRequestID, resultCode, resultDesc, true);
        }
    }
}
