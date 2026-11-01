package com.peterscode.edutrack.fees.enums;

/**
 * Type of a ledger entry. Amounts are signed: positive for charges, negative for credits.
 */
public enum LedgerEntryType {
    CHARGE,
    PAYMENT,
    DISCOUNT,
    WAIVER,
    ADJUSTMENT,
    REVERSAL
}
