package com.peterscode.edutrack.security;

public class PlanLimitReachedException extends RuntimeException {
    private final String errorCode;
    public PlanLimitReachedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}
