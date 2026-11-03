# Phase 0: Investigation Report — Concurrency Hardening

## 1. Transaction Inventory

### 1.1 All @Transactional Boundaries with Hazard Classification

| File | Method | Propagation | Isolation | ReadOnly | Hazard |
|------|--------|-------------|-----------|----------|--------|
| **StkPushService** | `initiate` | REQUIRED | DEFAULT | false | **(e) External call in transaction** — MpesaClient called while DB connection held. **(b) Swallowed exception** — catch(Exception) updates payment but rethrows FeesRuleException; transaction rolls back the payment status change. |
| **StkPushTimeoutJob** | `processStalePayments` | REQUIRED | DEFAULT | false | **(e) External call in transaction** — MpesaClient.queryStkPushStatus inside one transaction for ALL stale payments. **(f) Too long transaction** — loop over all stale payments in one transaction. |
| **StkCallbackService** | `processStkResult` | REQUIRED | DEFAULT | false | **(b) Swallowed exception** — catch(Exception) updates inbox but the payment changes may be rolled back inconsistently. |
| **StkCallbackService** | `resolveStalePayment` | REQUIRED | DEFAULT | false | None significant. |
| **PaymentPostingService** | `recordManualPayment` | REQUIRED | DEFAULT | false | **(a) Self-invocation** — `allocatePaymentToInvoices` is @Transactional but called from within the same class; proxy is bypassed. Uses non-locking `findBySchoolIdAndStudentId` instead of locking `findBySchoolIdAndId`. |
| **PaymentPostingService** | `allocatePaymentToInvoices` | REQUIRED | DEFAULT | false | **(a) Self-invocation** — never runs as a separate transaction when called from recordManualPayment. |
| **LibraryBorrowingService** | `issueBook` | REQUIRED | DEFAULT | false | No pessimistic locks. Check-then-act on BookCopy status and active loan without locking. |
| **LibraryBorrowingService** | `returnBook` | REQUIRED | DEFAULT | false | No pessimistic locks. Check-then-act on loan status without locking. |
| **LibraryBorrowingService** | `renewBook` | REQUIRED | DEFAULT | false | No pessimistic locks. Read-modify-write on loan without locking. |
| **LibraryBorrowingService** | `markLost` | REQUIRED | DEFAULT | false | No pessimistic locks. Check-then-act on loan status without locking. |
| **StudentServiceImpl** | `createStudent` | REQUIRED | READ_COMMITTED | false | Check-then-act on admissionNumber uniqueness (existsBy then save) — race window. |
| **StudentServiceImpl** | `updateStudentStatus` | REQUIRED | DEFAULT | false | PlanGuard.assertStudentCapacity uses FOR UPDATE on school, but count is a plain SELECT — stale in READ_COMMITTED. |
| **StudentServiceImpl** | `bulkUpdateStatus` | REQUIRED | READ_COMMITTED | false | Same plan limit race as updateStudentStatus. |
| **UserRegistrationService** | `signupInitialSuperAdmin` | REQUIRED | DEFAULT | false | Uses bootstrap_lock FOR UPDATE — good. But existsByRole(SUPER_ADMIN) is a plain SELECT after the lock — could be stale. |
| **UserRegistrationService** | `createTeacher` | REQUIRED | READ_COMMITTED | false | PlanGuard.assertTeacherCapacity — same count race. |
| **UserRegistrationService** | `createStudent` | REQUIRED | DEFAULT | false | No planGuard call — student creation via UserRegistrationService bypasses student capacity check. |
| **PlanGuard** | `assertStudentCapacity` | MANDATORY | DEFAULT | false | Locks school row with FOR UPDATE, then countBySchoolIdAndStatus is a plain SELECT — stale in READ_COMMITTED. |
| **PlanGuard** | `assertTeacherCapacity` | MANDATORY | DEFAULT | false | Same pattern as assertStudentCapacity. |
| **ExaminationService** | `registerStudent` | REQUIRED | DEFAULT | false | Check-then-act on duplicate registration (findBySchoolIdAndExamSeriesIdAndStudentId then save) — race window. |
| **AttendanceService** | `recordBatch` | REQUIRED | DEFAULT | false | Check-then-act on existing attendance (findBy then saveAll) — race window. Uses timetable lock only when lockTimetable=true. |
| **GradingService** | `submitScores` | REQUIRED | DEFAULT | false | Bulk save without checking for concurrent edits. |
| **GradingService** | `closeTerm` | REQUIRED | DEFAULT | false | Status change without lock. |
| **TimetableService** | `publish` | REQUIRED | DEFAULT | false | Uses findLocked (PESSIMISTIC_WRITE) — good. |
| **TimetableService** | `createLessons` | REQUIRED | DEFAULT | false | No timetable lock — race with publish. |
| **TimetablePublishedEventListener** | `onTimetablePublished` | DEFAULT | DEFAULT | false | **(g) Not AFTER_COMMIT** — @TransactionalEventListener without phase=AFTER_COMMIT. Runs before commit, so it sees data that may be rolled back. |
| **SchoolEntitlementsCacheListener** | `evict` | AFTER_COMMIT | - | - | Correct. |
| **AuthRateLimiter** | `onBadCredentials` | - | - | - | **(g) In-memory state** — ConcurrentHashMap not safe across instances. |
| **AuthRateLimiter** | `onAuthenticationSuccess` | - | - | - | Same. |
| **LmsFileCleanupJob** | `cleanup` | REQUIRED | DEFAULT | false | **(f) Too long transaction** — processes all orphaned files in one transaction. No SKIP LOCKED for multi-instance. |
| **PaymentInvoiceServiceImpl** | `expireInvoicesScheduled` | REQUIRED | DEFAULT | false | No SKIP LOCKED for multi-instance. |
| **LibraryAuditService** | `append` | REQUIRES_NEW | DEFAULT | false | **(c) REQUIRES_NEW** — audit row in separate transaction. If outer transaction rolls back, audit row remains — out of sync. |
| **StudentAuditService** | `record` | REQUIRES_NEW | DEFAULT | false | Same REQUIRES_NEW problem. |
| **FeeInvoiceService** | `generateInvoices` | REQUIRED | DEFAULT | false | Loop over students in one transaction — too long. |
| **C2bCallbackService** | `processC2bResult` | REQUIRED | DEFAULT | false | Similar to StkCallbackService — check-then-act on payment status. |

### 1.2 Summary of Transaction Traps

| Trap | Count | Files |
|------|-------|-------|
| (a) @Transactional on self-invoked methods | 2 | PaymentPostingService.allocatePaymentToInvoices |
| (b) Swallowed exceptions | 2 | StkPushService.initiate, StkCallbackService.processStkResult |
| (c) REQUIRES_NEW audit rows | 2 | LibraryAuditService, StudentAuditService |
| (e) External calls in lock-holding transactions | 2 | StkPushService, StkPushTimeoutJob |
| (f) Too long transactions (loops) | 3 | StkPushTimeoutJob, LmsFileCleanupJob, FeeInvoiceService |
| (g) Event listeners not AFTER_COMMIT | 1 | TimetablePublishedEventListener |

---

## 2. Hazard Catalogue

### 2.1 RACE (Check-Then-Act)

| # | File/Line | Interleaving | Invariant Violated | Severity | Current Protection | Proposed Fix |
|---|-----------|--------------|-------------------|----------|-------------------|--------------|
| R1 | StudentServiceImpl:83-99 | T1: existsByAdmissionNumber(false) → T2: existsByAdmissionNumber(false) → T1: save → T2: save | Duplicate admission numbers | **Critical** | uk_student_school_admission unique constraint | Constraint already exists. Service check is for friendly message. Map constraint violation to 409. |
| R2 | StkPushService:108-130 | T1: findByStatus(PENDING) empty → T2: findByStatus(PENDING) empty → T1: save PENDING → T2: save PENDING | Two pending STK payments for same student | **Critical** | None | Add unique constraint on (school_id, student_id, status) WHERE status='PENDING' via generated column. Or use locked check. |
| R3 | ExaminationService:registerStudent | T1: findBySeriesAndStudent empty → T2: findBySeriesAndStudent empty → T1: save → T2: save | Duplicate exam registration | **High** | None | Add unique constraint on (school_id, exam_series_id, student_id). |
| R4 | AttendanceService:98-101 | T1: findByStreamAndDate empty → T2: findByStreamAndDate empty → T1: saveAll → T2: saveAll | Duplicate attendance records | **High** | uk_attendance_school_student_date_scope unique constraint | Constraint already exists. Map to 409. |
| R5 | UserRegistrationService:77-81 | T1: FOR UPDATE bootstrap_lock → T1: existsByRole(SUPER_ADMIN)=false → T1 releases lock → T2: FOR UPDATE → T2: existsByRole=true → T2 fails | Actually safe — the lock serializes. But the lock is released between existsByRole and createUser if the method throws. | **Medium** | bootstrap_lock row FOR UPDATE | Already protected. Ensure the lock is held through the entire create. |
| R6 | PlanGuard:32-34 | T1: locks school → T1: count=limit-1 → T1 releases → T2: locks school → T2: count=limit-1 → T1: save student → T2: save student | Student count exceeds plan limit | **Critical** | School FOR UPDATE lock, but count is plain SELECT | In READ_COMMITTED, the count is a snapshot. Need to hold the school lock through the save, or use atomic UPDATE. |
| R7 | LibraryBorrowingService:48-60 | T1: findByBookNumber → T1: status=AVAILABLE → T2: findByBookNumber → T2: status=AVAILABLE → T1: save loan → T2: save loan | Same copy issued twice | **Critical** | @Version on BookCopy, but no pessimistic lock | Add PESSIMISTIC_WRITE lock on BookCopy. |
| R8 | LibraryBorrowingService:129-135 | T1: returnBook finds active loan → T2: markLost finds same active loan → T1: save RETURNED → T2: save LOST | Loan status inconsistency | **High** | @Version on Loan, but no pessimistic lock | Add PESSIMISTIC_WRITE lock on Loan via copy. |
| R9 | LibraryBorrowingService:193-198 | T1: renewBook finds ACTIVE → T2: returnBook finds ACTIVE → T1: save renewed → T2: save returned | Renew vs return race | **High** | @Version on Loan | Add PESSIMISTIC_WRITE lock on Loan. |
| R10 | PaymentPostingService:67-73 | T1: findBySchoolIdAndStudentId → T2: findBySchoolIdAndStudentId → T1: save payment → T2: save payment | Duplicate payment posting | **High** | @Version on StudentFeeAccount, but non-locking query | Use locking findBySchoolIdAndId for StudentFeeAccount. |

### 2.2 WRITE-WRITE (Lost Update)

| # | File/Line | Interleaving | Invariant Violated | Severity | Current Protection | Proposed Fix |
|---|-----------|--------------|-------------------|----------|-------------------|--------------|
| W1 | LibraryBorrowingService:113-114 | T1: read activeLoanCount=2 → T2: read activeLoanCount=2 → T1: save 3 → T2: save 3 | activeLoanCount wrong | **High** | @Version on LibraryBorrowerAccount | Use atomic UPDATE or PESSIMISTIC_WRITE lock. |
| W2 | LibraryBorrowingService:155 | Same pattern for decrement | activeLoanCount wrong | **Medium** | @Version | Same fix. |
| W3 | StudentServiceImpl:166-184 | T1: load student → T2: load student → T1: save (email change) → T2: save (status change) | Lost update on student | **Medium** | No @Version on Student | Add @Version to Student entity. |
| W4 | Multiple edit endpoints | Same load-modify-save pattern | Lost updates on any mutable entity | **Medium** | @Version on some entities | Add @Version to all mutable entities. |

### 2.3 READ-WRITE (Stale Decision)

| # | File/Line | Interleaving | Invariant Violated | Severity | Current Protection | Proposed Fix |
|---|-----------|--------------|-------------------|----------|-------------------|--------------|
| RW1 | PlanGuard:32-34 | T1: locks school → T1: count=limit-1 (stale snapshot) → T1: allows → T2 already saved student | Count exceeds limit | **Critical** | School FOR UPDATE, but count is plain SELECT | In READ_COMMITTED, plain SELECT sees snapshot. Need to hold lock through save or use atomic UPDATE. |
| RW2 | TimetableService:createLessons vs publish | T1: createLessons (no lock) → T2: publish (locks timetable) → T2 validates without T1's lessons | Published timetable missing lessons or with clashes | **High** | Only publish takes timetable lock | Take timetable lock in createLessons too, or validate inside publish. |

### 2.4 DEADLOCK RISK

| # | File/Line | Interleaving | Invariant Violated | Severity | Current Protection | Proposed Fix |
|---|-----------|--------------|-------------------|----------|-------------------|--------------|
| D1 | LibraryBorrowingService | T1: locks copy A → T1: waits for account B → T2: locks account B → T2: waits for copy A | Deadlock | **High** | None | Enforce global lock ordering: always lock BookCopy before LibraryBorrowerAccount. |
| D2 | PaymentPostingService | T1: locks account A → T1: waits for invoice B → T2: locks invoice B → T2: waits for account A | Deadlock | **Medium** | None | Enforce lock ordering: always lock StudentFeeAccount before FeeInvoice. |

### 2.5 DUPLICATE EXECUTION

| # | File/Line | Scenario | Invariant Violated | Severity | Current Protection | Proposed Fix |
|---|-----------|----------|-------------------|----------|-------------------|--------------|
| DU1 | StkPushTimeoutJob | Two instances run simultaneously, both process the same stale payment | Double status update, double posting | **Critical** | None | SKIP LOCKED claiming on fee_payments. Process each payment in its own transaction. |
| DU2 | LmsFileCleanupJob | Two instances run simultaneously | Double delete (harmless but wasteful) | **Low** | None | SKIP LOCKED or idempotent delete. |
| DU3 | PaymentInvoiceServiceImpl | Two instances run simultaneously | Double invoice expiry | **Low** | None | SKIP LOCKED or idempotent status change. |
| DU4 | StkCallbackService | Same callback delivered twice by provider | Double payment posting | **Critical** | Status check (PENDING → SUCCEEDED) | Add unique constraint on provider_receipt. Status check is good but needs @Version. |

### 2.6 SEQUENCE

| # | File/Line | Pattern | Invariant Violated | Severity | Current Protection | Proposed Fix |
|---|-----------|---------|-------------------|----------|-------------------|--------------|
| S1 | PaymentPostingService:196 | Receipt number: "RCP-" + schoolId + timestamp | Concurrent payments get same receipt number | **Medium** | Timestamp-based, not unique | Use database sequence or counter row. |
| S2 | ExaminationService | Candidate number generation | Duplicate candidate numbers | **High** | None visible | Use per-series counter row with unique constraint. |

### 2.7 CACHE-DB

| # | File/Line | Scenario | Invariant Violated | Severity | Current Protection | Proposed Fix |
|---|-----------|----------|-------------------|----------|-------------------|--------------|
| C1 | AuthRateLimiter | In-memory ConcurrentHashMap | Rate limit bypassed on second instance | **Medium** | In-memory only | Report limitation. Fix out of scope (needs Redis). |
| C2 | SchoolEntitlementsCacheListener | Cache eviction after commit | Stale entitlements until next write | **Low** | AFTER_COMMIT listener, evicts on write | Already correct. Verify TTL and key format. |

---

## 3. Isolation Analysis

### 3.1 Current State

- **No global isolation level** is set in `application.yaml`. MySQL InnoDB defaults to `REPEATABLE READ`.
- Per-method `isolation = Isolation.READ_COMMITTED` is set on:
  - `StudentServiceImpl.createStudent`
  - `StudentServiceImpl.updateStudent`
  - `StudentServiceImpl.bulkUpdateStatus`
  - `UserRegistrationService.createTeacher`
- `spring.jpa.open-in-view = false` — correct, no lazy loading surprises.
- `hibernate.jdbc.batch_size = 25` and `order_updates = true` — good for batching.

### 3.2 REPEATABLE READ vs READ COMMITTED

**InnoDB REPEATABLE READ behavior:**
- Plain `SELECT` reads from a consistent snapshot (start of transaction).
- `SELECT ... FOR UPDATE` reads the latest committed row and places an exclusive lock.
- Gap locks prevent phantom reads — this means `SELECT ... FOR UPDATE` on a range also locks the gap, increasing deadlock risk.
- Check-then-act with plain SELECT is **unsafe** because the snapshot may not see another transaction's uncommitted (or just-committed) row.

**InnoDB READ COMMITTED behavior:**
- Each plain `SELECT` reads the latest committed data (no snapshot across the transaction).
- No gap locks by default — only next-key locks on the exact rows matched by `SELECT ... FOR UPDATE`.
- Lower deadlock risk from gap locks, but higher stale-decision risk if you don't lock before reading.

### 3.3 Recommendation: READ COMMITTED Globally

**Reasons:**
1. The code already uses READ COMMITTED in critical paths (student creation, teacher creation).
2. READ COMMITTED eliminates gap locks, reducing deadlock risk in the library and payment modules.
3. With unique constraints as the final guard for check-then-insert, READ COMMITTED is safe for those patterns.
4. For counters and limits, the lock-then-read pattern (PlanGuard) works correctly in READ COMMITTED because the FOR UPDATE lock serializes access.

**Migration risk:** Low. The application already uses READ COMMITTED in key methods. Setting it globally just removes the per-method annotations.

**Setting:** Add to `application.yaml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        connection_provider_disables_autocommit: true
  datasource:
    hikari:
      connection-init-sql: "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED"
```

Or use `spring.jpa.properties.hibernate.dialect` with a custom dialect. The cleanest approach for Boot 4 / Hibernate 7 is:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
    open-in-view: false
  datasource:
    hikari:
      connection-init-sql: "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED"
```

**Verification needed:** Confirm `connection-init-sql` property name in Boot 4 / HikariCP 6.x. Mark as UNVERIFIED if unsure.

### 3.4 Lock Behavior in READ COMMITTED

- `SELECT ... FOR UPDATE` — reads latest committed row, places exclusive lock. No gap lock.
- `SELECT ... FOR UPDATE SKIP LOCKED` — skips rows locked by other transactions. Available in MySQL 8.0+.
- Deadlock detection: MySQL automatically detects and rolls back one transaction with error 1213.
- Lock wait timeout: Default 50 seconds (`innodb_lock_wait_timeout`). Should be reduced to 5-10 seconds for API responsiveness.

---

## 4. Lost-Update Exposure

### 4.1 Entities WITH @Version

| Entity | Version Type | Notes |
|--------|-------------|-------|
| StudentAttendance | Long | OK |
| ExamRegistration | Long | OK |
| FeePayment | Integer | OK |
| SchoolPaymentConfig | Integer | OK |
| StudentFeeAccount | Integer | OK |
| StudentScore | Long | OK |
| BookCopy | Long | OK |
| LibraryBorrowerAccount | Long | OK |
| LibraryFine | Long | OK |
| Loan | Long | OK |
| LmsAssignment | Long | OK |
| LmsCourse | Long | OK |
| LmsResource | Long | OK |
| LmsSubmission | Long | OK |
| SchoolStorageUsage | Long | OK |
| Timetable | Long | OK |

### 4.2 Entities WITHOUT @Version (Mutable)

| Entity | Mutability | Risk |
|--------|-----------|------|
| **Student** | High — edited by admins, status changed | **Critical** — concurrent edits lose data |
| **School** | Medium — subscription, modules, limits edited | **High** — concurrent admin edits |
| **User** | Medium — role changes, status | **Medium** |
| **Staff** | Medium — edited by admins | **Medium** |
| **AcademicYear** | Low — current flag toggled | **Low** |
| **ClassLevel** | Low — name, active | **Low** |
| **Stream** | Low — name, active | **Low** |
| **Term** | Medium — start/end dates, active | **Medium** |
| **TimetableLesson** | Medium — created/edited/deleted | **High** — concurrent timetable edits |
| **Period** | Low — rarely edited | **Low** |
| **Room** | Low — rarely edited | **Low** |
| **ExamSeries** | Medium — status changes | **Medium** |
| **FeeInvoice** | Medium — paid amount, status | **High** — payment allocation |
| **LedgerEntry** | Append-only | **Low** — no edits |
| **PaymentAllocation** | Append-only | **Low** |
| **Guardian** | Medium — edited | **Medium** |
| **ParentStudentMapping** | Append-only | **Low** |
| **Book** | Low — catalogue info | **Low** |
| **BorrowingPolicy** | Low — rarely edited | **Low** |
| **LibraryCategory** | Low — rarely edited | **Low** |
| **FeeStructure** | Medium — edited | **Medium** |
| **FeeItem** | Medium — edited | **Medium** |
| **UnallocatedPayment** | Medium — status changes | **Medium** |
| **CallbackInbox** | Medium — processed flag | **Low** |
| **ClassLevelSubject** | Low | **Low** |
| **StreamSubject** | Low | **Low** |
| **TimetableAudit** | Append-only | **Low** |
| **LmsAnnouncement** | Medium | **Low** |
| **LmsAttachment** | Low | **Low** |
| **LmsTopic** | Low | **Low** |
| **StoredFile** | Medium — status changes | **Medium** |
| **AssessmentPaper** | Medium | **Medium** |
| **AssessmentType** | Low | **Low** |
| **GradeScale** | Medium | **Medium** |
| **GradeScaleRange** | Medium | **Medium** |
| **SubjectAssessment** | Medium | **Medium** |
| **TermGrade** | Medium | **Medium** |
| **PaymentInvoice** (payment) | Medium | **Medium** |
| **PaymentTransaction** | Append-only | **Low** |
| **SubscriptionPrice** | Low | **Low** |

### 4.3 Bulk Updates Bypassing @Version

| File/Line | Query | Bypasses Version | Risk |
|-----------|-------|-----------------|------|
| AcademicYearRepository:33 | `UPDATE AcademicYear a SET a.current = false` | Yes — no @Version on AcademicYear | Low |
| CallbackInboxRepository:29 | `DELETE FROM CallbackInbox ci WHERE ...` | N/A — cleanup | Low |
| LibraryBorrowerAccountRepository:22 | `INSERT INTO library_borrower_accounts ... ON DUPLICATE KEY UPDATE` | N/A — upsert for create-if-absent | Low |

---

## 5. Unique Constraints Inventory

### 5.1 Existing Unique Constraints

| Constraint | Table | Columns | Purpose |
|------------|-------|---------|---------|
| uk_student_school_admission | students | (school_id, admission_number) | Student admission uniqueness |
| uk_student_school_email | students | (school_id, email) | Student email uniqueness |
| uk_student_user_id | students | (user_id) | User-student link uniqueness |
| uk_school_code | schools | (code) | School code uniqueness |
| uk_school_subdomain | schools | (subdomain) | School subdomain uniqueness |
| uk_guardian_school_email | guardians | (school_id, email) | Guardian email uniqueness |
| uk_subject_school_code | subjects | (school_id, code) | Subject code uniqueness |
| uk_stream_school_code | streams | (school_id, code) | Stream code uniqueness |
| uk_class_level_school_name | class_levels | (school_id, name) | Class level uniqueness |
| uk_timetable_school_term | timetables | (school_id, term_id) | One timetable per term |
| uk_lesson_stream_slot | timetable_lessons | (stream_id, day_of_week, period_id, timetable_id) | Stream slot uniqueness |
| uk_lesson_teacher_slot | timetable_lessons | (teacher_id, day_of_week, period_id, timetable_id) | Teacher slot uniqueness |
| uk_lesson_room_slot | timetable_lessons | (room_id, day_of_week, period_id, timetable_id) | Room slot uniqueness |
| uk_attendance_school_student_date_scope | student_attendance | (school_id, student_id, attendance_date, attendance_type, timetable_lesson_id) | Attendance uniqueness |
| uk_stream_subject_year | stream_subjects | (school_id, stream_id, subject_id, academic_year_id) | Stream subject assignment |
| uk_class_level_subject | class_level_subjects | (class_level_id, subject_id) | Class level curriculum |
| uk_fee_payment_checkout_request | fee_payments | (checkout_request_id) | STK checkout uniqueness |
| uk_fee_payment_provider_receipt | fee_payments | (provider_receipt) | Provider receipt uniqueness |
| uk_callback_inbox_provider | callback_inbox | (school_id, channel, provider_id) | Callback idempotency |
| uk_student_fee_account | student_fee_accounts | (school_id, student_id) | Fee account uniqueness |
| uk_exam_registration | exam_registrations | (school_id, exam_series_id, student_id, assessment_paper_id) | Exam registration uniqueness |
| uk_library_borrower_account | library_borrower_accounts | (school_id, borrower_type, borrower_id) | Borrower account uniqueness |
| uk_book_copy_number | book_copies | (school_id, book_number) | Book copy uniqueness |
| uk_loan_active | library_loans | (copy_id, active_marker) | Active loan uniqueness |
| uk_school_payment_config | school_payment_configs | (school_id) | Payment config uniqueness |
| uk_storage_usage | school_storage_usage | (school_id) | Storage usage uniqueness |

### 5.2 Missing Unique Constraints (Needed for Concurrency)

| Table | Columns | Purpose | Priority |
|-------|---------|---------|----------|
| fee_payments | (school_id, student_id, status) WHERE status='PENDING' | One pending STK per student | **Critical** |
| exam_registrations | Already has uk_exam_registration | Already covered | N/A |
| student_attendance | Already has uk_attendance_school_student_date_scope | Already covered | N/A |

**Note:** The partial unique index on fee_payments (WHERE status='PENDING') requires a generated column in MySQL:
```sql
ALTER TABLE fee_payments ADD COLUMN is_pending TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) STORED;
CREATE UNIQUE INDEX uk_pending_stk_payment ON fee_payments(school_id, student_id, is_pending) WHERE is_pending = 1;
```
MySQL 8.0.13+ supports filtered indexes via `INVISIBLE` or functional key parts. Verify syntax.

---

## 6. Scheduled Jobs Analysis

| Job | Interval | Multi-Instance Safe | Idempotent | Transaction Size | External Calls |
|-----|----------|-------------------|------------|-----------------|----------------|
| StkPushTimeoutJob | 5 min | **No** | Partially | All stale payments in one TX | Yes — MpesaClient |
| LmsFileCleanupJob | 24 hours | **No** | Yes (delete) | All orphaned files in one TX | File I/O |
| PaymentInvoiceServiceImpl | 1 hour | **No** | Yes (status change) | All invoices in one TX | No |

---

## 7. Cache Analysis

### 7.1 Cache Configuration

- Redis caching is **optional** (`edutrack.cache.enabled=${EDUTRACK_CACHE_ENABLED:false}`).
- When disabled, `NoOpCacheManager` is used — all `@Cacheable` calls fall through to DB.
- Cache keys include school_id where applicable.
- TTL is configurable per cache name via `edutrack.cache.ttl`.

### 7.2 Cache Keys and Eviction

| Cache Name | Read (@Cacheable) | Write (@CacheEvict) | Key Includes school_id | TTL |
|------------|-------------------|---------------------|----------------------|-----|
| classlevels | getActiveClassLevels | create, update, delete | Yes | Configurable |
| streams | getStreamsByClassLevel | create, update, delete | Yes | Configurable |
| school-profile | getSchoolProfile | update | Yes | Configurable |
| school-public | getSchoolBySubdomain | update | Yes | Configurable |
| timetable-views | lessonView, teacherView, studentView | createLessons, publish, etc. | Yes | Configurable |
| subjects | listSubjects | create, update, delete | Yes | Configurable |
| fee-structures | getActiveFeeStructure | create, update | Yes | Configurable |
| grading | getAssessmentTypes, getGradeScales | create, update | Yes | Configurable |
| library-catalogue | searchBooks | create, update | Yes | Configurable |

### 7.3 Cache Risks

- **AuthRateLimiter**: In-memory ConcurrentHashMap — not shared across instances. Rate limit can be bypassed by hitting a different instance. **Out of scope** fix (needs Redis).
- **SchoolEntitlementsCacheListener**: Uses AFTER_COMMIT — correct. Evicts on `SchoolEntitlementsEvicted` event.

---

## 8. Decisions Confirmed

| Decision | Value | Rationale |
|----------|-------|-----------|
| Global isolation level | READ COMMITTED | Reduces gap locks, already used in key methods |
| Job claiming | Database SKIP LOCKED | No new infrastructure, default per task |
| Idempotency store | Database table | Survives restarts, no Redis dependency |
| Retry mechanism | In-project retry helper | Small utility, 3 attempts, exponential backoff with jitter |

---

## 9. Decisions Needed From You

| # | Decision | Options | Recommendation |
|---|----------|---------|----------------|
| 1 | Which entities require @Version on API edit endpoints? | All mutable vs. high-risk only | All mutable entities listed in §4.2 |
| 2 | Lock wait timeout value? | 5s, 10s, 30s | 5 seconds for API, 30s for jobs |
| 3 | Retry base delay and cap? | 100ms/1s, 500ms/5s | 100ms base, 1s cap, 3 attempts |
| 4 | Pending STK payment constraint approach? | Generated column + filtered index vs. application-level lock | Generated column — verify MySQL 8 syntax |
| 5 | Candidate number generation method? | Counter row vs. UUID-based | Counter row with unique constraint |
| 6 | Receipt number generation method? | Counter row vs. UUID-based | Counter row or UUID to avoid collisions |

---

## 10. UNVERIFIED Items

| Item | Why | Action |
|------|-----|--------|
| `connection-init-sql` property name in Boot 4 / HikariCP 6.x | API may have changed | Verify against Boot 4 docs |
| MySQL 8 filtered unique index syntax | `WHERE` clause on CREATE INDEX may vary | Verify against MySQL 8 docs |
| Hibernate 7 `@Lock(PESSIMISTIC_WRITE)` behavior in READ COMMITTED | May differ from Hibernate 6 | Verify against Hibernate 7 docs |
| Spring Boot 4 `spring.data.redis` property paths | May have changed from Boot 3 | Verify against Boot 4 docs |
| Jackson 3 API changes | New major version | Verify DTO serialization |

---

## 11. Summary of Hazards by Severity

| Severity | Count | Hazards |
|----------|-------|---------|
| **Critical** | 6 | R1(admission dup), R2(STK pending dup), R6(plan limit race), R7(library double issue), DU1(timeout job double exec), DU4(callback double posting) |
| **High** | 10 | R3(exam dup), R4(attendance dup), R8(return vs lost), R9(renew vs return), R10(payment posting), W1(activeLoanCount), RW1(stale count), RW2(create vs publish), D1(library deadlock), S2(candidate numbers) |
| **Medium** | 8 | R5(signup — already safe), W2(activeLoanCount decrement), W3(student edits), W4(generic edits), C1(rate limiter), S1(receipt numbers), DU2(LMS cleanup), DU3(invoice expiry) |
| **Low** | 4 | DU2, DU3, bulk updates, cache staleness |
