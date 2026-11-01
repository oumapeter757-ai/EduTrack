package com.peterscode.edutrack.fees.enums;

/**
 * Status of a fee payment.
 */
public enum FeePaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    UNALLOCATED,
    HELD,
    REVERSED
}
