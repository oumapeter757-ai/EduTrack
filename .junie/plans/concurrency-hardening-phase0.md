---
sessionId: session-261103-114016-13kg
---

# Testing

### Validation Approach

All concurrency tests use a shared `ConcurrencyTestBase` with:
- Testcontainers MySQL 8 with real Flyway migrations
- No `@Transactional` on test methods (real commits required)
- `CyclicBarrier` or `CountDownLatch` to release N threads simultaneously
- `ExecutorService` with configurable thread count and timeout
- `@RepeatedTest(50)` or loop-based repetition to expose timing issues
- Invariant assertions after all threads complete
- Cleanup of test data after each test

### Key Scenarios

**Library concurrency (LibraryConcurrencyTest):**
1. **Double issue:** 10 threads try to issue the same copy. Exactly 1 succeeds, 9 get 409.
2. **Issue vs lost:** Thread A issues, Thread B marks lost. One winner, no deadlock error.
3. **Return vs renew:** Thread A returns, Thread B renews. One winner, no error.
4. **Two fine assessments:** Two threads assess fines for the same overdue loan. One fine created.
5. **Deadlock test:** Two threads take BookCopy and LibraryBorrowerAccount in opposite orders. Test passes if no deadlock error reaches the client (retry helper handles it).

**Payment concurrency (PaymentConcurrencyTest):**
1. **50 concurrent payments:** 50 threads post payments to the same student account. Balance equals sum of ledger entries. No duplicate postings.
2. **Duplicate callback:** Same provider receipt processed twice. Exactly one posting.
3. **STK timeout vs late callback:** Timeout job and callback race on the same payment. Exactly one status update.
4. **Reversal:** Concurrent reversal and payment. Balance is consistent.

**Student concurrency (StudentConcurrencyTest):**
1. **Plan limit race:** School at limit-1. 10 threads create students. Exactly 1 succeeds, 9 get 403 or 409. Total active never exceeds limit.
2. **Duplicate admission number:** Two threads create with same number. One succeeds, one gets 409.
3. **Reactivation:** Same as creation but with status change.

**Signup concurrency (SignupConcurrencyTest):**
1. **First SUPER_ADMIN:** 10 threads attempt signup with valid token. Exactly 1 succeeds.

**Timetable concurrency (TimetableConcurrencyTest):**
1. **Concurrent lesson creation:** Two threads create lessons in the same slot. One succeeds, one gets 409.
2. **Create vs publish:** Thread A creates lessons, Thread B publishes. Either the lesson is in the published set or rejected.
3. **Teacher reassignment vs create:** Thread A reassigns teacher, Thread B creates lesson. No silent inconsistency.

**Attendance concurrency (AttendanceConcurrencyTest):**
1. **Double submit:** Two threads submit attendance for the same student/session. One succeeds, one gets 409.

**Examination concurrency (ExaminationConcurrencyTest):**
1. **Duplicate registration:** Two threads register same student for same series/subject. One succeeds, one gets 409.
2. **Candidate number uniqueness:** Under load, no duplicate candidate numbers.

**Job concurrency (JobConcurrencyTest):**
1. **Two runners:** Two instances of the timeout job run simultaneously. They claim disjoint rows (no overlap).
2. **Expired lease:** A runner crashes (simulated), lease expires, another runner claims the row.

**Transaction hygiene (TransactionHygieneTest):**
1. **Rollback on runtime exception:** Partial data is rolled back.
2. **Swallowed exception:** No partial data left.
3. **Event listener on rollback:** Listener doesn't run when transaction rolls back (AFTER_COMMIT).
4. **Audit row rollback:** Audit row is rolled back with its action.
5. **No external call in lock:** Fake Mpesa client asserts no DB connection held during call.

**Retry (RetryTest):**
1. **Forced deadlock succeeds on retry:** Simulate deadlock on first attempt, succeed on second.
2. **Bounded retries:** After 3 attempts, return 409/503.
3. **Non-retryable not retried:** Validation error not retried.
4. **No double side effects:** Retried operation doesn't execute side effects twice.

### Edge Cases

- Deadlock detection: MySQL error 1213 mapped to retryable exception
- Lock timeout: MySQL error 1205 mapped to retryable exception
- @Version mismatch: ObjectOptimisticLockingFailureException mapped to 409 VERSION_CONFLICT
- DataIntegrityViolationException: mapped by constraint name to specific 409 code
- Concurrent idempotency key insert: unique constraint decides winner, loser gets 409
- Idempotency key with different payload: returns 422
- Expired idempotency key: new execution allowed
- Job lease expiry: row returned to queue

### Test Changes

- All new tests in `src/test/java/com/peterscode/edutrack/concurrency/`
- Existing tests should continue to pass (regression)
- No existing tests modified
- Concurrency tests marked with `@Tag("concurrency")` for selective execution

# Delivery Steps

### ✓ Step 1: Phase 0: Investigation report and decisions
Deliver the complete Phase 0 investigation document with hazard catalogue, transaction inventory, isolation analysis, and lost-update exposure. Stop for your approval before any code changes.

- Compile the transaction inventory table (all @Transactional boundaries with hazard classification)
- Compile the hazard catalogue (all RACE, WRITE-WRITE, READ-WRITE, DEADLOCK, DUPLICATE, SEQUENCE, CACHE-DB findings with file/line, interleaving, severity, protection, proposed fix)
- Document isolation analysis (current vs proposed READ COMMITTED, lock behavior, deadlock risk)
- Document lost-update exposure (entities without @Version, bulk updates bypassing version)
- List decisions confirmed: READ COMMITTED, SKIP LOCKED, DB idempotency table, in-project retry helper
- List remaining decisions needed from you

### ✓ Step 2: Phase 1: Concurrency test harness and failing tests
Build the test infrastructure and write failing concurrency tests for all Critical and High hazards. Commit tests in a failing state so the hazards are visible.

- Create ConcurrencyTestBase with Testcontainers MySQL, Flyway, CyclicBarrier harness
- Write LibraryConcurrencyTest (double issue, issue vs lost, return vs renew, deadlock)
- Write PaymentConcurrencyTest (50 concurrent payments, duplicate callback, timeout vs callback)
- Write StudentConcurrencyTest (plan limit race, duplicate admission)
- Write SignupConcurrencyTest (exactly one SUPER_ADMIN)
- Write TimetableConcurrencyTest (concurrent lesson creation, create vs publish)
- Write AttendanceConcurrencyTest (double submit)
- Write ExaminationConcurrencyTest (duplicate registration, candidate uniqueness)
- Write JobConcurrencyTest (two runners, expired lease)
- Write TransactionHygieneTest (rollback, swallowed exception, event listener, audit row)
- Write RetryTest (deadlock retry, bounded, non-retryable)
- Verify all tests FAIL on current code
- Commit with clear messages marking tests as intentionally failing

### ✓ Step 3: Phase 2: Transaction hygiene fixes
Fix every transactional trap from Phase 0.1 with minimal changes. Move external calls out of lock-holding transactions. Fix event listeners.

- StkPushService: commit PENDING payment first, call MpesaClient outside transaction
- StkPushTimeoutJob: process each payment in its own transaction, move Mpesa query outside
- PaymentPostingService: use locking findBySchoolIdAndId for StudentFeeAccount
- TimetablePublishedEventListener: change to AFTER_COMMIT
- Add docs/transaction-rules.md
- Set global READ COMMITTED in application.yaml
- Remove per-method isolation=READ_COMMITTED duplicates
- Verify TransactionHygieneTest passes
- Stop for your approval

### ✓ Step 4: Phase 3: Write-write conflict fixes
Add @Version to all mutable entities, fix library locking, replace read-modify-write counters with atomic updates.

- Migration V47: add version columns to 24 entities
- Add @Version field to each entity
- LibraryBorrowingService: PESSIMISTIC_WRITE locks on BookCopy and LibraryBorrowerAccount
- Replace activeLoanCount read-modify-write with atomic UPDATE
- Global lock-ordering rule documented in docs/lock-ordering.md
- API: edit endpoints accept and return version field
- GlobalExceptionHandler: map version conflict to 409 VERSION_CONFLICT
- Verify LibraryConcurrencyTest and StudentConcurrencyTest pass

### ✓ Step 5: Phase 4: Race condition fixes
Add unique constraints, idempotency table, retry helper, and locked check-then-act for remaining races.

- Migration V48: unique constraints (pending STK payment, exam registration)
- Migration V50: idempotency_keys table
- IdempotencyService: check-before-execute with unique constraint guard
- RetryHelper: bounded retry with exponential backoff and jitter
- Apply retry helper to library operations, payment posting, student creation
- StkPushService: unique constraint on pending payments as final guard
- ExaminationService: unique constraint on registration as final guard
- Global lock-ordering enforced in code and tests
- Stop for your approval

### ✓ Step 6: Phase 5: Duplicate execution protection
Make scheduled jobs safe for multi-instance with SKIP LOCKED claiming. Make payment callbacks idempotent.

- Migration V49: job claim columns (claimed_by, claimed_until)
- StkPushTimeoutJob: claim bounded batch with SKIP LOCKED, process in small transactions
- LmsFileCleanupJob: same pattern
- PaymentInvoiceServiceImpl: same pattern
- StkCallbackService and C2bCallbackService: verify idempotency by provider transaction ID
- Timeout job vs late callback: @Version or status check prevents double posting
- Verify JobConcurrencyTest passes

### ✓ Step 7: Phase 6: Cache consistency and final verification
Verify cache eviction, TTL, and tenant isolation. Final test run. Git operations and summary.

- Verify @CacheEvict on all write paths
- Verify cache keys include school_id
- Verify TTL on all cache entries
- AuthRateLimiter: report multi-instance limitation (out of scope fix)
- Run full test suite: ./mvnw clean verify
- Create branch, commit, push
- Prepare PR description
- Deliver final summary with all 12 required sections