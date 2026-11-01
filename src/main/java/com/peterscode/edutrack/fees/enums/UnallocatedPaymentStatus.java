package com.peterscode.edutrack.fees.enums;

/**
 * Status of an unallocated payment awaiting resolution.
 */
public enum UnallocatedPaymentStatus {
    PENDING,
    ALLOCATED,
    REFUND_REQUESTED,
    IGNORED
}
