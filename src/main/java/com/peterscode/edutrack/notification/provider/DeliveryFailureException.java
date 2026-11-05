package com.peterscode.edutrack.notification.provider;

public class DeliveryFailureException extends Exception {

    private final String errorCode;
    private final boolean retryable;
    private final boolean outcomeUnknown;

    public DeliveryFailureException(String errorCode, boolean retryable, boolean outcomeUnknown) {
        super(errorCode);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.outcomeUnknown = outcomeUnknown;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
