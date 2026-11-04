# EduTrack backend security review

## Scope and limitations

Static, read-only review of the current checkout. No exploits, network requests,
database operations, builds, or tests were run. No repository files were changed
during the review; this report was added only after the user selected the
"Commit a summary of findings" follow-up.

The review inventoried 37 controllers / 246 mapped operations, entity and
repository filenames by module, and 50 Flyway migrations. It did **not** finish
a route-by-route authorization and tenant-source audit, a method-by-method
repository tenant-scope audit, or a line-by-line review of all migrations.
Selected source paths and migrations were inspected; unreviewed behaviors are
not represented as verified. Initial and final pre-report `git status
--porcelain` were non-empty and matched. They showed pre-existing changes in
`.junie/plans/concurrency-hardening-phase0.md`, cache/school/security/student/
user production and test files, deleted `UserNotFound.java`, and untracked
`SchoolEntitlementsDto.java` and `src/test/java/com/peterscode/edutrack/audit/`.
Those changes were not cleaned up or included in the report commit.

## Findings

| # | Severity | Category | File:line | Confidence | Finding |
|---|---|---|---|---|---|
| 1 | HIGH | authn / subscription-bypass | `src/main/java/com/peterscode/edutrack/payment/controller/MpesaWebhookController.java:30-36` | CONFIRMED | Public confirmation callback accepts a reported success and can mark the invoice confirmed and activate its subscription without verified callback authenticity or amount. |
| 2 | HIGH | config / data-exposure | `src/main/java/com/peterscode/edutrack/config/ApplicationConfig.java:76-83` | CONFIRMED | Outside the test profile, a blank encryption-key setting falls back to a source-known key for stored payment credentials. The key value is intentionally not reproduced here. |
| 3 | MEDIUM | data-exposure | `src/main/java/com/peterscode/edutrack/payment/controller/PaymentController.java:50-54` | CONFIRMED | Public invoice lookup returns billing and payment metadata for a supplied invoice number. |
| 4 | MEDIUM | data-exposure / escalation-horizontal | `src/main/java/com/peterscode/edutrack/lms/service/LmsResourceService.java:113-131` | CONFIRMED | Student resource listing returns resources without filtering publication/visibility state. |

### High-severity evidence and local reproduction descriptions

**1. Forged payment confirmation.** `SecurityConfig.java:48-50` permits the
payment callback path; `MpesaWebhookController.java:30-36` exposes the
confirmation handler. In `MpesaCallbackServiceImpl.java:81-114`, a zero
`ResultCode` leads to invoice lookup and the code sets
`invoice.setStatus(PaymentInvoiceStatus.CONFIRMED)` before calling
`schoolService.activateSubscriptionByPayment(invoice.getId())`.
`SchoolServiceImpl.java:487-496` sets the subscription status to `ACTIVE`.
For local reproduction only, submit an unauthenticated POST to
`/api/v1/payments/callback/confirmation` with a successful callback result and
an `Account_Reference` containing a pending invoice number. No request was
sent during this review. A forged callback can grant a paid subscription
without payment. Authenticate/independently verify the provider transaction
and check invoice, amount, and currency before a valid state transition.
Estimated effort: medium.

**2. Source-known credential encryption fallback.** `ApplicationConfig.java:76-83`
marks the bean `@Profile("!test")` and uses a fixed fallback if the configured
encryption key is blank. `CredentialEncryptionService.java:26-36` uses the
provided value as the AES key. If a non-test deployment omits configuration,
encrypted provider credentials can be decrypted by anyone with the source
fallback. Fail startup outside an explicit development profile if the key is
missing and use managed secrets with key rotation. Estimated effort: low to
medium. Secret value: **[REDACTED]**.

## Step 0 inventory (partial)

The controller inventory identified the following route families. This
grouping is **not** a per-handler matrix of authorization annotations,
`permitAll`, school-ID source, or SUPER_ADMIN requirement; those were not all
verified.

| Controller/module | Route families found |
|---|---|
| AcademicYear / no module | `/api/v1/academic-years` and `/{id}`, `/current`, `/{id}/set-current` |
| Attendance / ATTENDANCE | `/api/v1/attendance/batch`, `/stream`, `/me` |
| Auth / no module | `/api/v1/auth/login`, `/signup` |
| CacheAdmin / no module + active school | `/api/v1/admin/cache/evict`, `/clear`, `/stats` |
| ClassLevel, Stream / no module | `/api/v1/class-levels`, `/api/v1/streams`, nested class-level and ID routes |
| Examination / EXAMINATION | `/api/v1/exams/series`, registrations and student registration views |
| Fees / BILLING; C2bWebhook / no module | `/api/v1/fees` structures, invoices, payments, parent/student views, reports/config; callback routes |
| Grading / GRADING | `/api/v1/grading` scales, assessments, scores, grades, reports |
| Guardian, Parent / no module | `/api/v1/guardians`, `/api/v1/parents`, child-link routes |
| Library / LIBRARY | `/api/v1/library` borrowing, catalogue, borrower views, reports |
| LMS / LMS | `/api/v1/lms` courses, assignments, submissions, topics, resources, files, parent/student views |
| Payment / no module | `/api/v1/payments` invoice, history, admin views, callbacks; `/api/v1/subscription-prices` |
| School / no module | `/api/v1/schools` profile, status, subscription, module, lookup and entitlement routes |
| Staff / TEACHER_MANAGEMENT; Student / STUDENT_MANAGEMENT | `/api/v1/staff`, `/api/v1/students` CRUD, search, stats, status and linking |
| Subject / no module | `/api/v1/subjects` and class-level routes |
| Timetable / TIMETABLE; Term / no module | periods, rooms, timetables, lessons, streams, curriculum, terms |
| UserManagement / no module + active school | `/api/v1/admin` role-specific user-creation routes |

Entity/repository filename inventory (tenant scoping of every method was not
verified):

| Module | Entity-package classes | Repository classes |
|---|---|---|
| Academic year | `AcademicYear` | `AcademicYearRepository` |
| Attendance | `StudentAttendance`, status/type enums | `StudentAttendanceRepository` |
| Class levels | `ClassLevel`, `Stream` | `ClassLevelRepository`, `StreamRepository` |
| Examination | `ExamRegistration`, `ExamSeries` | `ExamRegistrationRepository`, `ExamSeriesRepository` |
| Fees | callback inbox, invoices/lines, items, payments, structures, ledger, allocations, payment config, student accounts, unallocated payments | callback/invoice/payment/structure/ledger/allocation/config/account repositories |
| Grading | assessment papers/types, scales/ranges, scores, subject assessments, term grades | corresponding assessment, scale, score and grade repositories |
| Guardian | `Guardian`, `ParentStudentMapping` | `GuardianRepository`, `ParentStudentMappingRepository` |
| Library | books/copies/categories, policies, borrower accounts, fines, loans | book/copy/category/policy/borrower/fine/loan repositories |
| LMS | courses, assignments, attachments, announcements, resources, submissions, topics, stored files, storage usage, status/type enums | LMS announcement/assignment/attachment/audit/course/resource/submission/topic/storage/file repositories |
| Payment | invoices, transactions, subscription prices | `PaymentInvoiceRepository`, `PaymentTransactionRepository`, `SubscriptionPriceRepository` |
| School | `School` | `SchoolRepository` |
| Shared | — | `IdempotencyKeyRepository` |
| Staff | `Staff` | `StaffRepository` |
| Student | `Student` | `StudentRepository`, `StudentSpecification` |
| Subject | `Subject` | `SubjectRepository`, `SubjectSpecification` |
| Timetable | class-level subjects, periods, rooms, stream subjects, terms, timetables, audit, lessons | corresponding timetable repositories |
| User | `Role`, `User` | `UserRepository` |

Role policy evidence is incomplete. `Role.java:9-54` defines account-creation
hierarchy; the inspected policy permits SUPER_ADMIN to create SUPER_ADMIN and
SCHOOL_ADMIN, while SCHOOL_ADMIN can create same-school staff/student/parent/
librarian/accountant roles. Other role-to-module CRUD permissions were not
verified handler by handler.

Filter/aspect order observed: `SecurityConfig.java:81` inserts
`JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`;
`SecurityConfig.java:42-61` configures HTTP authorization and stateless
sessions; method security is enabled at lines 20-21.
`ModuleAccessAspect.java:27-28` uses `@Order(201)`. The authorization-order
test `ModuleAccessAspectOrderTest` asserts a role denial happens before
entitlement loading. A second servlet-filter registration was not established.

## Step 6 known checks

Only checks supported by evidence examined are marked partial; the rest are
UNVERIFIED rather than presumed fixed or open.

| # | Status | Evidence / limitation |
|---:|---|---|
| 1 | PARTIAL | `SchoolController.java:60-67` and `SchoolServiceImpl` profile update enforce role/tenant checks; mapper `ZoneId.of` validation is at `SchoolMapper.java:216-219`. Currency-specific restriction and every timezone write path were not verified; DTO regex at `UpdateSchoolProfileRequestDto.java:49` may reject some valid IANA IDs. |
| 2 | PARTIAL | A timezone validation path was observed at `SchoolMapper.java:216-219`; every write path and invalid-timezone behavior were not traced. |
| 3 | UNVERIFIED | Filter source was inspected, but evidence for both account flags and blocking inactive schools on every non-module endpoint was not established. |
| 4 | UNVERIFIED | Not checked across all UserManagementController and SchoolController writes. |
| 5 | PARTIAL | The inspected profile update checks role/tenant, but reachability of both named principal-less service overloads was not fully established. |
| 6 | PARTIAL | `PlanGuard` row lock and transaction requirement were observed; all creation/reactivation/bulk paths and statistics equivalence were not verified. |
| 7 | UNVERIFIED | All teacher creation and reactivation paths were not traced. |
| 8 | PARTIAL | Uniqueness migrations were inventoried and selected migrations inspected; entity/migration parity and conflict-to-409 behavior were not exhaustively checked. |
| 9 | UNVERIFIED | Blank optional-string normalization and duplicate-null behavior were not verified. |
| 10 | UNVERIFIED | Positive limits, DB checks, and equality of effective-limit logic were not verified. |
| 11 | UNVERIFIED | Bootstrap token, constant-time comparison, seeded lock row, public aliases, and rate-limit details were not verified together. |
| 12 | UNVERIFIED | All login pre/post-checks, response hygiene, and inactive-school exception mapping were not verified. |
| 13 | UNVERIFIED | Rate limiter keying, trusted proxy handling, failure counting, and filter registration count were not verified. |
| 14 | UNVERIFIED | Global pageable whitelists, caps, and LIKE escaping were not fully traced. |
| 15 | PARTIAL | School mutation cache aspect was inspected; eviction after commit for every mutation and multi-instance behavior were not established. |
| 16 | UNVERIFIED | Custom ObjectMapper date serialization was not verified. |
| 17 | PARTIAL | A controller annotation validator exists, but all 246 mappings were not individually checked against `permitAll` and method security. |
| 18 | PARTIAL | Module aspect and selected explicit service checks were observed; all scheduler/listener/async callers were not enumerated. |

## Verified protections observed

- Password encoding uses `BCryptPasswordEncoder` (`ApplicationConfig.java:54-57`).
- The inspected JWT filter reloads the user record rather than using token role
  claims alone; full account-status enforcement remains unverified.
- Non-super module checks resolve school context from the principal and reject
  mismatched explicit school IDs (`ModuleAccessAspect.java:73-82`).
- `PlanGuard` locks the school row and requires an existing transaction in the
  inspected changed implementation.
- Inspected LMS file downloads compare file school ID with principal school ID
  (`LmsFileController.java:51-57`).
- Security headers include HSTS, content-type options, and frame denial
  (`SecurityConfig.java:72-78`).

## Top priorities

1. Authenticate and verify payment-provider confirmation before activating a
   subscription.
2. Remove the non-test fixed-key fallback and fail closed when no secure key is
   configured.
3. Restrict invoice lookup and minimize its public response.
4. Filter student-visible LMS resources by publication and visibility.
5. Complete route-by-route authorization/tenant-source and repository-scope
   verification, then resolve the UNVERIFIED Step 6 checks.

## Remediation follow-up

The four findings above were addressed after the static review:

1. Payment callback handlers now require a configured shared callback token,
   compare it in constant time, verify payment amount/receipt/state/expiry, and
   lock invoices during callback transitions. Set
   `MPESA_PAYMENT_WEBHOOK_TOKEN` and include it as the `token` query parameter
   on the payment provider's validation and confirmation callback URLs; use a
   random token with at least 32 characters and redact it from infrastructure
   request logs. The secret itself is not recorded here.
2. The source-known encryption-key fallback was removed. Configure
   `EDUTRACK_FEES_ENCRYPTION_KEY` before starting any non-test profile. The
   known hard-coded fallback passkey in the Daraja client was also removed.
3. Invoice lookup now requires SUPER_ADMIN or SCHOOL_ADMIN; school admins are
   restricted to invoices belonging to their own school.
4. Student resource lists now contain only published resources whose
   `visibleFrom` is null or has arrived; repository queries also include
   `school_id`.

Access/refresh token support was added: access JWTs default to 15 minutes,
opaque refresh tokens expire after seven days by default, are stored only as
SHA-256 hashes, rotate on refresh, and can be revoked at logout. Configure
`JWT_ACCESS_EXPIRATION` and `JWT_REFRESH_EXPIRATION` in milliseconds to change
these lifetimes. `POST /api/v1/auth/refresh` accepts `{"refreshToken":"..."}`;
`POST /api/v1/auth/logout` revokes the submitted refresh token. The refreshed
response retains the existing `token` access-token field and adds
`refreshToken`, `expiresIn`, and `refreshTokenExpiresIn`.

Production and test-source compilation succeeded, and the isolated refresh
token unit test passed with `mvn -q -Dtest=RefreshTokenServiceTest test`.
Database-backed integration tests and Flyway/database validation were not run
because the available Spring integration-test configuration can
connect to a local MySQL instance and was not proven isolated from shared data.
The grouped inventory and incomplete route/repository/migration coverage above
remain limitations of the review; remediation does not constitute a complete
security audit.
