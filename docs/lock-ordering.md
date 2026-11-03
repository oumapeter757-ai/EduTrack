# Lock Ordering Rules

Global lock-ordering rule to prevent deadlocks in concurrent operations.

## Rule

Always acquire row locks in this order:

1. **School** (tenant root)
2. **Parent aggregate** (Student, Staff, Guardian, Timetable)
3. **Children** (BookCopy, LibraryBorrowerAccount, TimetableLesson, FeeInvoice)
4. **Ledger / counter rows** (LedgerEntry, StudentFeeAccount, SchoolStorageUsage)

Never acquire a lower-level lock before releasing a higher-level lock within the same transaction.

## Library Lock Map

| Operation | Lock Order | Lock Type |
|-----------|-----------|-----------|
| issueBook | BookCopy → LibraryBorrowerAccount | PESSIMISTIC_WRITE |
| returnBook | BookCopy → LibraryBorrowerAccount | PESSIMISTIC_WRITE |
| markLost | BookCopy → LibraryBorrowerAccount | PESSIMISTIC_WRITE |
| renewBook | Loan (by ID) | @Version (optimistic) |

The BookCopy is always locked before the LibraryBorrowerAccount. This prevents deadlocks when two threads try to issue different copies to the same borrower, or the same copy to different borrowers.

## Payment Lock Map

| Operation | Lock Order | Lock Type |
|-----------|-----------|-----------|
| postPayment | StudentFeeAccount (by schoolId and id) | PESSIMISTIC_WRITE |
| allocatePayment | StudentFeeAccount → FeeInvoice | PESSIMISTIC_WRITE |

## Student Lock Map

| Operation | Lock Order | Lock Type |
|-----------|-----------|-----------|
| createStudent | School (FOR UPDATE) → PlanGuard check | PESSIMISTIC_WRITE |
| reactivateStudent | School (FOR UPDATE) → PlanGuard check | PESSIMISTIC_WRITE |

## Timetable Lock Map

| Operation | Lock Order | Lock Type |
|-----------|-----------|-----------|
| createLesson | Timetable (FOR UPDATE) | PESSIMISTIC_WRITE |
| publishTimetable | Timetable (FOR UPDATE) | PESSIMISTIC_WRITE |
| deleteLesson | Timetable (FOR UPDATE) | PESSIMISTIC_WRITE |
| moveLesson | Timetable (FOR UPDATE) | PESSIMISTIC_WRITE |

## Notes

- Prefer locking by primary key to avoid range locks.
- Keep transactions short: lock → read → decide → write → commit.
- Never hold row locks across external calls (M-Pesa, SMTP, HTTP).
- Use @Version for optimistic locking on single-row edits (renewBook, student profile edit).
- Use PESSIMISTIC_WRITE for check-then-act patterns where the check and act must be atomic.
- Set `innodb_lock_wait_timeout` to a bounded value (e.g., 5s) so lock waits fail fast.
