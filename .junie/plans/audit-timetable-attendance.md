---
sessionId: session-261031-131115-1dbc
---

# Requirements

## Overview & Goals

Conduct a comprehensive security audit of the **TIMETABLE** and **ATTENDANCE** modules in EduTrack — a multi-tenant school management SaaS built with Spring Boot 4.1.1, Java 25, MySQL (Flyway migrations), Lombok, and Caffeine caching. The audit covers correctness, data leakage, subscription wiring, tenant isolation, and authorization.

## Scope

### In Scope
- All code in `com.peterscode.edutrack.timetable` and `com.peterscode.edutrack.attendance` packages (controllers, services, DTOs, entities, repositories, mappers, exceptions, events)
- All related migrations: `V21__create_timetable_module.sql`, `V22__create_attendance_module.sql`
- Security infrastructure: `ModuleAccessAspect`, `ModuleAccessService`, `SchoolEntitlements`, `@RequiresModule`, `@NoModuleRequired`, `@RequiresActiveSchool`, `SchoolMutationCacheAspect`, `SchoolEntitlementsCacheListener`, `ControllerAnnotationValidator`
- `GlobalExceptionHandler` for error mapping
- `StreamSubjectController` (teacher assignments, curriculum) — shared between TIMETABLE and TEACHER_MANAGEMENT modules
- `TermController` — marked `@NoModuleRequired`, lives in timetable package
- All existing tests for these modules

### Out of Scope (report only, do not fix)
- Issues in other modules (student, staff, guardian, etc.) unless the module mapping is unambiguous
- Infrastructure outside the audit scope (JWT, CORS, auth flow)

## User Stories
- As a FREE-tier school, I can use ATTENDANCE (daily register) but not TIMETABLE
- As a BASIC+ school, I can use both ATTENDANCE and TIMETABLE
- As a teacher, I can only see my own timetable and record attendance for my assigned streams
- As a student, I can only see my stream's timetable and my own attendance
- As a SUPER_ADMIN, I must pass an explicit schoolId and can manage any school
- No school can read another school's data

## Phases
1. **Phase A**: Read-only audit report (no code changes)
2. **Phase B**: Proof — attack tests that FAIL on current code
3. **Phase C**: Fix Critical and High findings
4. **Phase D**: Wire missing subscription gates
5. **Phase E**: Fix Medium and Low findings, regression

# Technical Design

## Current Implementation

### Project Architecture
- **Base package**: `com.peterscode.edutrack`
- **Database**: MySQL (runtime), H2 (test only). Flyway migrations, `ddl-auto: validate`
- **Security**: JWT stateless auth, `@PreAuthorize` role checks, `@RequiresModule` / `@NoModuleRequired` annotations enforced by `ModuleAccessAspect` (Order 201)
- **Subscription**: `ServiceModule.getDefaultModulesForTier()` is source of truth. FREE gets STUDENT_MANAGEMENT, TEACHER_MANAGEMENT, ATTENDANCE. BASIC+ adds TIMETABLE.
- **Tenant boundary**: `school_id` on every entity. `SchoolEntitlements` computed per request from DB (status, dates, timezone, enabledModules)
- **Caching**: Caffeine cache in `ModuleAccessService`, keyed by `schoolId`, 30s TTL, evicted via `SchoolEntitlementsEvicted` event after commit
- **Jackson**: `fail-on-unknown-properties: true` (application.yaml:6)
- **Roles**: SUPER_ADMIN, SCHOOL_ADMIN, TEACHER, STUDENT, PARENT (Role.java)
- **Subscription tiers**: FREE, BASIC, STANDARD, PREMIUM, ENTERPRISE
- **Subscription statuses**: TRIAL, ACTIVE, GRACE, SUSPENDED, EXPIRED, CANCELLED

### Controllers Under Audit

| Controller | Module Annotation | Endpoints |
|---|---|---|
| `TimetableController` | `@RequiresModule(TIMETABLE)` (class-level) | 16 endpoints: periods, rooms, timetables, lessons, publish/unpublish, my timetable |
| `TermController` | `@NoModuleRequired` (class-level) | 3 endpoints: createTerm, getTerms, getTermById |
| `StreamSubjectController` | No class-level annotation | 5 endpoints: curriculum (TIMETABLE), assignments (TEACHER_MANAGEMENT) |
| `AttendanceController` | `@RequiresModule(ATTENDANCE)` (class-level) | 3 endpoints: recordBatch, streamAttendance, myAttendance |

### Key Findings from Code Review

**A1. Subscription Wiring**
- `TimetableController`: class-level `@RequiresModule(TIMETABLE)` — all 16 endpoints gated ✓
- `AttendanceController`: class-level `@RequiresModule(ATTENDANCE)` — all 3 endpoints gated ✓
- `TermController`: `@NoModuleRequired` — FREE schools can manage terms ✓ (correct, terms needed for daily attendance)
- `StreamSubjectController`: **No class-level annotation**. Method-level annotations on all 5 methods ✓. But curriculum methods use `@RequiresModule(TIMETABLE)` while assignment methods use `@RequiresModule(TEACHER_MANAGEMENT)` — mixed modules in one controller
- `ModuleAccessAspect`: SUPER_ADMIN bypasses module check (line 106-108) — correct per design
- `ModuleAccessAspect`: EXPIRED status allows read-only (GET/HEAD/OPTIONS), denies writes with 402 ✓
- `ModuleAccessAspect`: GRACE status treated as active ✓
- `ModuleAccessAspect`: SUSPENDED/CANCELLED denied ✓
- `AttendanceService.recordBatch`: checks TIMETABLE module for PERIOD attendance at service level (line 67-69) ✓
- `AttendanceService.streamAttendance`: checks TIMETABLE module for PERIOD attendance at service level (line 137-139) ✓
- `SchoolMutationCacheAspect`: evicts cache on `update*`, `toggle*`, `create*` in SchoolServiceImpl ✓
- `SchoolEntitlementsCacheListener`: evicts after commit ✓
- `ControllerAnnotationValidator`: startup check fails when controller has no annotation ✓

**A1 Issues Found:**
- `ModuleAccessAspect` line 106-108: SUPER_ADMIN bypasses ALL module checks — a SUPER_ADMIN can access TIMETABLE for a FREE school. This may be intentional but should be documented.
- `SchoolMutationCacheAspect` line 12-14: only matches `SchoolServiceImpl.update*`, `toggle*`, `create*` — does NOT match `updateSchoolSubscription` specifically if the method name differs. Need to verify.
- `TimetableService.audit()` line 684: uses `LocalDateTime.now()` without Clock — audit timestamps not deterministic
- `TimetableService.requireWritableTerm()` line 603: uses `LocalDate.now(schoolZone(schoolId))` — Clock is injected but `schoolZone` catches RuntimeException and falls back to UTC, which is reasonable

**A2. Tenant Isolation**
- All timetable repositories use `findBySchoolIdAndId` or `findBySchoolIdAnd...` patterns ✓
- `TimetableLessonRepository`: all queries include `schoolId` ✓
- `StudentAttendanceRepository`: all queries include `schoolId` ✓
- `TimetableService.schoolId()` method (line 658-669): resolves school from principal or explicit param, validates SUPER_ADMIN school exists ✓
- `AttendanceService.schoolId()` method (line 277-298): same pattern ✓
- **Issue**: `TimetableService.audit()` line 684: `LocalDateTime.now()` — should use injected `clock`
- **Issue**: `TimetableLessonRepository.findPublishedTeacherLessons` (line 53-54): joins Timetable but only filters by `l.schoolId` — the timetable's schoolId is not explicitly checked in the WHERE (though `l.schoolId` should match since lessons carry schoolId). The join to Timetable is implicit in the JPQL but `t.schoolId` is not checked.
- **Issue**: `TimetableLessonRepository.findPublishedStreamLessons` (line 56-57): same pattern — joins Timetable but doesn't check `t.schoolId`

**A3. Data Exposure**
- `LessonView` (TimetableViews.java:20-21): exposes `id`, `streamSubjectId`, `streamId`, `teacherId`, `roomId`, `periodId` — internal IDs visible to all roles
- `StudentLessonView` (line 22): only subject, teacher name, room, times, day — no internal IDs ✓
- `TeacherLessonView` (line 23): subject, stream name, room, times, day — no internal IDs ✓
- `StudentAttendanceView` (AttendanceViews.java:12-22): exposes `id`, `studentId`, `streamId`, `timetableLessonId`, `remarks`, `updatedAt` — internal IDs and remarks visible
- **Issue**: `StudentAttendanceView` exposes `remarks` to all viewers including students via `myAttendance` — remarks should be admin-only
- **Issue**: `StudentAttendanceView` exposes `updatedAt` (audit data) to all roles
- **Issue**: `LessonView` exposes internal IDs to admin views — acceptable for admins but should be checked
- **Issue**: `myStudentLessons` and `myTeacherLessons` query `findPublishedStreamLessons` / `findPublishedTeacherLessons` — these return lessons from **every published term**, not just the current term

**A4. Information Leak**
- `GlobalExceptionHandler`: `ModuleAccessDeniedException` handler (line 80-90) logs schoolId, userId, module, errorCode — acceptable for audit
- `GlobalExceptionHandler`: `TimetableRuleException` handler (line 92-95) returns `ex.getMessage()` and `ex.getErrorCode()` in response — message may leak internal details
- `GlobalExceptionHandler`: `AttendanceRuleException` handler (line 97-100): same pattern
- `GlobalExceptionHandler`: `DataIntegrityViolationException` handler (line 192-233): returns constraint-specific messages — may leak schema info
- **Issue**: `TimetableService` exception messages like "Teacher not found", "Stream not found" could be used as oracle to enumerate resources
- **Issue**: `ModuleAccessAspect` line 79-82: when explicitSchoolId doesn't match principal's schoolId, returns `RESOURCE_NOT_FOUND` — but when it matches, proceeds. This creates a 404 vs 200 oracle for school membership

**A5. Authorization**
- All controller methods have `@PreAuthorize` ✓
- Service-level `requireAdmin()` and `requireRecorder()` checks ✓
- `fail-on-unknown-properties: true` in application.yaml ✓
- **Issue**: `StreamSubjectController` has no class-level module annotation — relies on method-level only. The `ControllerAnnotationValidator` allows this because each method has its own annotation.

**A6. Correctness**
- Migrations have named unique constraints ✓
- `uk_lesson_stream_slot`, `uk_lesson_teacher_slot`, `uk_lesson_room_slot` ✓
- `uk_timetable_school_term` ✓
- `uk_attendance_school_student_date_scope` ✓
- `chk_attendance_type`, `chk_attendance_status`, `chk_attendance_scope` ✓
- `DataIntegrityViolationException` mapped to 409 for known constraints ✓
- `Timetable` has `@Version` (optimistic locking) ✓
- `StudentAttendance` has `@Version` ✓
- `TimetableRepository.findLocked()` uses `PESSIMISTIC_WRITE` ✓
- **Issue**: `createLessons` (line 329-397) calls `findLocked` ✓, but `moveLesson` (line 399-426) and `deleteLesson` (line 428-439) do NOT lock the timetable — race condition with publish
- **Issue**: `publish` event fired inside transaction (line 468) — `TimetablePublishedEvent` fires synchronously within the same transaction
- **Issue**: `audit()` line 684 uses `LocalDateTime.now()` instead of Clock
- **Issue**: `findPublishedTeacherLessons` and `findPublishedStreamLessons` return lessons from ALL published terms, not filtered by current term

**A7. Known Suspected Problems**
- **CONFIRMED**: `moveLesson` and `deleteLesson` do not lock the timetable (line 399, 428) vs `publish` does (line 455)
- **CONFIRMED**: `audit()` uses `LocalDateTime.now()` (line 684)
- **CONFIRMED**: `findPublishedTeacherLessons` / `findPublishedStreamLessons` return all published terms
- **PARTLY TRUE**: `createLessons` does check academic year match (line 348-350) — already fixed
- **NOT REPRODUCED**: `findByIdAndSchoolId` argument swap — the code uses `findBySchoolIdAndId(schoolId, id)` consistently
- **CONFIRMED**: `publish` event fires inside transaction (line 468)

**A8. Test Gaps**
- `TimetableAttackTest`: 5 attack tests (Clock injection, class level mismatch, inactive room, period break conversion, term controller endpoints)
- `AttendanceAttackTest`: 4 attack tests (TIMETABLE module check, Clock injection, future date, FREE tier daily attendance)
- Missing: endpoint-matrix test (unauthenticated, wrong role, other school, FREE school, expired school), concurrency tests, draft visibility tests, student A vs B tests

### Key Decisions Needed
1. Should SUPER_ADMIN bypass module checks for all schools, or should they be subject to the same module gates?
2. Should `remarks` in `StudentAttendanceView` be hidden from students?
3. Should `myTeacherLessons` / `myStudentLessons` filter to the current term only?
4. Should `moveLesson` and `deleteLesson` acquire pessimistic locks like `publish`?
5. Should the `TimetablePublishedEvent` fire after commit instead of inside the transaction?

# Delivery Steps

### ✓ Step 1: Phase A: Read-only audit report
Produce the complete audit report with all 9 sections (A1-A9), citing file paths and line numbers for every finding. This is a documentation-only phase — no code changes.

- **A1**: Subscription wiring matrix for all 4 controllers (TimetableController, TermController, StreamSubjectController, AttendanceController) with annotation status, gate completeness, and evaluation order analysis
- **A2**: Tenant-isolation table for every repository method — verify school_id in WHERE clause, flag any findById without school, verify argument order consistency
- **A3**: Data-exposure matrix — map every endpoint × role to response fields, flag internal IDs, audit data, and personal data exposure
- **A4**: Information-leak channels — error messages, exception handlers, log statements, validation echoes, sequential ID enumeration
- **A5**: Authorization and mass assignment — verify @PreAuthorize on every method, Jackson unknown property rejection, entity binding
- **A6**: Correctness and integrity — migration constraints, locking, transaction boundaries, Clock usage, N+1 queries, lifecycle rules
- **A7**: Verify each of the 11 known suspected timetable problems as CONFIRMED, NOT REPRODUCED, ALREADY FIXED, or PARTLY TRUE with evidence
- **A8**: Test gap analysis — list which of the 12 required test scenarios exist vs missing
- **A9**: Findings table ranked by severity with ID, file/line, evidence, exploit scenario, proposed fix, and test name
- List decisions needed from user before Phase B

### ✓ Step 2: Phase B: Write attack tests that fail
For each Critical and High finding from Phase A, write automated tests that FAIL on the current code.

### ✓ Step 3: Phase C: Fix Critical and High findings

- Create a parametrized endpoint-matrix test covering ALL endpoints of both modules asserting: unauthenticated → 401, wrong role → 403, other school → 404 with generic body, FREE school → 403 MODULE_NOT_IN_PLAN on TIMETABLE endpoints, expired school (past grace) → read allowed, write denied (402), module disabled in enabledModules → denied
- Write concurrency tests for slot uniqueness and session uniqueness
- Write tests for draft invisibility to students and teachers
- Write tests for student A vs student B isolation and teacher A vs teacher B register isolation
- Verify all tests FAIL on current code and show the failures
- Use Testcontainers with MySQL and real Flyway migrations for integration tests; H2 for unit tests only

### ✓ Step 3: Phase C: Fix Critical and High findings
Fix each Critical and High finding in small commits, each with its failing test now passing.

- Fix `moveLesson` and `deleteLesson` to acquire pessimistic locks on the timetable (use `findLocked`)
- Fix `audit()` to use injected `Clock` instead of `LocalDateTime.now()`
- Fix `findPublishedTeacherLessons` and `findPublishedStreamLessons` to filter by current term or add term filtering
- Fix `TimetablePublishedEvent` to fire after commit (use `@TransactionalEventListener` with `AFTER_COMMIT` phase or move event publishing)
- Fix data exposure: hide `remarks` from students in `StudentAttendanceView`, hide internal IDs where appropriate
- Fix tenant isolation: add `t.schoolId` check to published lesson queries
- Standardize tenant-scoped lookups on `(schoolId, id)` argument order
- Centralize school resolution and module checks
- Each fix in a separate commit with its test passing

### ✓ Step 4: Phase D: Wire missing subscription gates
Complete the subscription wiring identified in A1.

- Add `@RequiresModule` or `@NoModuleRequired` annotations to any unannotated controllers or methods
- Ensure `SchoolMutationCacheAspect` covers all subscription mutation paths including `updateSchoolSubscription`
- Add service-level TIMETABLE module check for lesson-mode attendance (already exists, verify completeness)
- Verify term management is NOT behind TIMETABLE gate (TermController has @NoModuleRequired ✓)
- Add error codes: MODULE_NOT_IN_PLAN (403), SUBSCRIPTION_EXPIRED (402), SCHOOL_INACTIVE (403), ACCESS_DENIED (403) with ApiResponse errorCode
- Ensure background code (event listeners, schedulers) calls entitlement checks
- Verify startup check fails when a controller has no annotation
- List every annotation added to controllers outside the two modules
- Stop for user approval after this phase

### ✓ Step 5: Phase E: Fix Medium/Low and final regression
Fix remaining confirmed Medium and Low findings, then run a final regression.

- Fix remaining data exposure issues (internal IDs in DTOs, audit data leakage)
- Fix information leak channels (error messages, exception handler improvements)
- Fix any remaining correctness issues (N+1 queries, null handling, edge cases)
- Add any missing migration constraints with pre-checks for existing duplicates
- Run `./mvnw clean verify` and ensure all tests pass
- Produce the final summary with all 11 required sections
- Create branch `fix/audit-timetable-attendance`, prepare PR description
- Verify no secrets in commits or diff

---

# Phase A: Read-Only Audit Report

## A1. Subscription Wiring Matrix

| Controller | Annotation | Level | Module | Gate Complete? | Notes |
|---|---|---|---|---|---|
| `TimetableController` | `@RequiresModule(TIMETABLE)` | Class (line 20) | TIMETABLE | ✓ Yes | All 16 endpoints inherit the gate |
| `TermController` | `@NoModuleRequired` | Class (line 19) | None | ✓ Yes | FREE schools can manage terms — correct |
| `StreamSubjectController` | None (class-level) | — | Mixed | ✓ Yes | 5 methods all have method-level annotations |
| `StreamSubjectController.saveCurriculum` | `@RequiresModule(TIMETABLE)` | Method (line 29) | TIMETABLE | ✓ | |
| `StreamSubjectController.curriculum` | `@RequiresModule(TIMETABLE)` | Method (line 37) | TIMETABLE | ✓ | |
| `StreamSubjectController.apply` | `@RequiresModule(TIMETABLE)` | Method (line 45) | TIMETABLE | ✓ | |
| `StreamSubjectController.assign` | `@RequiresModule(TEACHER_MANAGEMENT)` | Method (line 54) | TEACHER_MANAGEMENT | ✓ | |
| `StreamSubjectController.assignments` | `@RequiresModule(TEACHER_MANAGEMENT)` | Method (line 62) | TEACHER_MANAGEMENT | ✓ | |
| `AttendanceController` | `@RequiresModule(ATTENDANCE)` | Class (line 27) | ATTENDANCE | ✓ Yes | All 3 endpoints inherit the gate |

### A1.1 Gate Completeness Analysis

**ModuleAccessAspect** (`security/ModuleAccessAspect.java`):
- Order 201 — runs after Spring Security but before business logic ✓
- Evaluation order: authenticated → role (@PreAuthorize) → tenant (schoolId resolution) → module/subscription ✓
- SUPER_ADMIN bypasses ALL module checks (line 106-108) — **by design**, documented
- EXPIRED status: read-only (GET/HEAD/OPTIONS allowed), writes denied with 402 (line 111-115) ✓
- GRACE status: treated as active (line 116-117) ✓
- SUSPENDED/CANCELLED: denied with ACCESS_DENIED (line 116-119) ✓
- Fail-closed on error: catches RuntimeException and denies access (line 90-95) ✓
- Clock usage: `ModuleAccessService` uses injected `Clock` for effective status computation (line 41) ✓

**ModuleAccessService** (`security/ModuleAccessService.java`):
- `entitlements()` uses injected Clock with school timezone (line 41) ✓
- FREE tier never expires — returns ACTIVE (line 74-76) ✓
- Cache: Caffeine, keyed by schoolId only, 30s TTL (line 25-26) ✓
- `assertModuleEnabled()` for service-level checks (line 116-137) ✓
- Checks both `enabledModules` AND `tierModules` via `hasModule()` (SchoolEntitlements.java:15-17) ✓

**SchoolMutationCacheAspect** (`security/SchoolMutationCacheAspect.java`):
- Matches `SchoolServiceImpl.update*`, `toggle*`, `create*` (line 12-14)
- `updateSchoolSubscription` matches `update*` pattern ✓ — **verified**
- Publishes `SchoolEntitlementsEvicted` event (line 19) ✓

**SchoolEntitlementsCacheListener** (`security/SchoolEntitlementsCacheListener.java`):
- `@TransactionalEventListener(phase = AFTER_COMMIT)` (line 12) ✓
- Evicts cache after commit ✓

**ControllerAnnotationValidator** (`config/ControllerAnnotationValidator.java`):
- Startup check: fails when controller has no annotation (line 25-26) ✓
- Allows method-level annotations when class has none (line 20-24) ✓
- Requires @PreAuthorize on NoModuleRequired endpoints (line 29-32) ✓

**Service-level TIMETABLE check for PERIOD attendance**:
- `AttendanceService.recordBatch` line 67-69: checks TIMETABLE for PERIOD type ✓
- `AttendanceService.streamAttendance` line 137-139: checks TIMETABLE for PERIOD type ✓

**Term management**: `TermController` has `@NoModuleRequired` — NOT behind TIMETABLE gate ✓

### A1.2 Issues Found

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| A1-1 | Low | `ModuleAccessAspect.java:106-108` | SUPER_ADMIN bypasses ALL module checks — can access TIMETABLE for FREE school. By design but should be documented. |
| A1-2 | Medium | `ModuleAccessAspect.java:79-82` | When explicitSchoolId doesn't match principal's schoolId, returns 404 — creates existence oracle for school membership |

---

## A2. Tenant Isolation Table

### A2.1 Timetable Repositories

| Repository | Method | schoolId in WHERE? | Argument Order | Notes |
|---|---|---|---|---|
| `TimetableRepository` | `findBySchoolIdAndId` | ✓ | (schoolId, id) | |
| `TimetableRepository` | `findBySchoolIdAndTermId` | ✓ | (schoolId, termId) | |
| `TimetableRepository` | `findLocked` | ✓ | (schoolId, id) | PESSIMISTIC_WRITE |
| `TimetableLessonRepository` | `findBySchoolIdAndTimetableIdOrderBy...` | ✓ | (schoolId, timetableId) | |
| `TimetableLessonRepository` | `findBySchoolIdAndId` | ✓ | (schoolId, id) | |
| `TimetableLessonRepository` | `existsPublishedTeachingAssignment` | ✓ | schoolId param | Checks both `l.schoolId` AND `t.schoolId` (line 16) ✓ |
| `TimetableLessonRepository` | `findBySchoolIdAndStreamSubjectId` | ✓ | (schoolId, streamSubjectId) | |
| `TimetableLessonRepository` | `countBySchoolIdAndTimetableIdAndStreamSubjectId` | ✓ | (schoolId, timetableId, streamSubjectId) | |
| `TimetableLessonRepository` | `countSlotConflicts` | ✓ | school param | |
| `TimetableLessonRepository` | `countAssignmentForDay` | ✓ | school param | |
| `TimetableLessonRepository` | `countTeacherForDay` | ✓ | school param | |
| `TimetableLessonRepository` | `countSameSubjectForDay` | ✓ | school param | |
| `TimetableLessonRepository` | `findTeacherLessons` | ✓ | school param | |
| `TimetableLessonRepository` | `countPublishedPeriodUse` | ✓ | school param | Joins Timetable but checks only `l.schoolId` (line 47) |
| `TimetableLessonRepository` | `countPublishedRoomUse` | ✓ | school param | Joins Timetable but checks only `l.schoolId` (line 50) |
| `TimetableLessonRepository` | `findPublishedTeacherLessons` | ⚠️ | school param | Joins Timetable but does NOT check `t.schoolId` (line 53-54) |
| `TimetableLessonRepository` | `findPublishedStreamLessons` | ⚠️ | school param | Joins Timetable but does NOT check `t.schoolId` (line 56-57) |
| `PeriodRepository` | `findBySchoolIdOrderBySortOrder` | ✓ | (schoolId) | |
| `PeriodRepository` | `findBySchoolIdAndId` | ✓ | (schoolId, id) | |
| `RoomRepository` | `findBySchoolIdOrderByName` | ✓ | (schoolId) | |
| `RoomRepository` | `findBySchoolIdAndId` | ✓ | (schoolId, id) | |
| `TermRepository` | `findBySchoolIdAndId` | ✓ | (schoolId, id) | |
| `TermRepository` | `findBySchoolIdAndIdAndActiveTrue` | ✓ | (schoolId, id) | |
| `TermRepository` | `existsBySchoolIdAndAcademicYearIdAndName` | ✓ | (schoolId, academicYearId, name) | |
| `StreamSubjectRepository` | `findBySchoolIdAndStreamIdAndAcademicYearId` | ✓ | (schoolId, streamId, academicYearId) | |
| `StreamSubjectRepository` | `findBySchoolIdAndAcademicYearId` | ✓ | (schoolId, academicYearId) | |
| `StreamSubjectRepository` | `findBySchoolIdAndId` | ✓ | (schoolId, id) | |
| `StreamSubjectRepository` | `sumWeeklyLessons` | ✓ | school param | |
| `ClassLevelSubjectRepository` | `findBySchoolIdAndClassLevelId` | ✓ | (schoolId, classLevelId) | |
| `ClassLevelSubjectRepository` | `findBySchoolIdAndClassLevelIdAndSubjectId` | ✓ | (schoolId, classLevelId, subjectId) | |

### A2.2 Attendance Repositories

| Repository | Method | schoolId in WHERE? | Argument Order | Notes |
|---|---|---|---|---|
| `StudentAttendanceRepository` | `findBySchoolIdAndTermIdAndStreamIdAndAttendanceDateAndAttendanceTypeAndTimetableLessonId` | ✓ | (schoolId, ...) | |
| `StudentAttendanceRepository` | `findBySchoolIdAndStudentIdOrderByAttendanceDateDescIdDesc` | ✓ | (schoolId, studentId) | |
| `StudentAttendanceRepository` | `existsAttendanceForTimetable` | ✓ | schoolId param | Checks both `a.schoolId` AND `l.schoolId` (line 20) ✓ |

### A2.3 Issues Found

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| A2-1 | Medium | `TimetableLessonRepository.java:53-54` | `findPublishedTeacherLessons` joins Timetable but does NOT check `t.schoolId` — theoretical cross-tenant leak if lesson.schoolId differs from timetable.schoolId |
| A2-2 | Medium | `TimetableLessonRepository.java:56-57` | `findPublishedStreamLessons` — same issue as A2-1 |
| A2-3 | Low | `TimetableLessonRepository.java:47` | `countPublishedPeriodUse` — same issue (low risk since count is used for validation) |
| A2-4 | Low | `TimetableLessonRepository.java:50` | `countPublishedRoomUse` — same issue |

**Note**: In practice, A2-1 through A2-4 are unlikely to be exploitable because `schoolId` is denormalized on `TimetableLesson` and should always match the timetable's `schoolId` (enforced by FK constraints). However, the JPQL should explicitly check `t.schoolId = :schoolId` for defense-in-depth.

### A2.4 Argument Order Consistency

All `findBySchoolIdAndId` methods use consistent `(schoolId, id)` order ✓. No argument swap issues found — **NOT REPRODUCED** as suspected.

---

## A3. Data Exposure Matrix

### A3.1 DTO Field Analysis

| DTO | Fields | Internal IDs? | Audit Data? | Personal Data? | Used By |
|---|---|---|---|---|---|
| `LessonView` | id, streamSubjectId, streamId, teacherId, roomId, periodId, dayOfWeek, subjectName, teacherDisplayName, roomName, startTime, endTime | ✓ All IDs | ✗ | Teacher name only | Admin endpoints |
| `StudentLessonView` | subject, teacher, room, startTime, endTime, dayOfWeek | ✗ | ✗ | Teacher name | Student "my timetable" |
| `TeacherLessonView` | subject, stream, room, startTime, endTime, dayOfWeek | ✗ | ✗ | ✗ | Teacher "my timetable" |
| `StudentAttendanceView` | id, studentId, studentName, streamId, attendanceDate, attendanceType, timetableLessonId, status, **remarks**, **updatedAt** | ✓ id, studentId, streamId, timetableLessonId | ✓ updatedAt | ✓ remarks (admin notes) | All roles |
| `AssignmentView` | id, streamId, subjectId, academicYearId, teacherId, lessonsPerWeek | ✓ All IDs | ✗ | ✗ | Admin endpoints |
| `CurriculumView` | id, classLevelId, subjectId, lessonsPerWeek, compulsory | ✓ All IDs | ✗ | ✗ | Admin endpoints |
| `TimetableView` | id, termId, status, publishedAt, version | ✓ id, termId | ✓ publishedAt, version | ✗ | Admin endpoints |
| `BatchResponse` | rosterSize, createdCount, updatedCount, attendance (List<StudentAttendanceView>) | Via StudentAttendanceView | Via StudentAttendanceView | Via StudentAttendanceView | Admin/Teacher |

### A3.2 Endpoint × Role Response Analysis

| Endpoint | Roles | Response DTO | Issues |
|---|---|---|---|
| `GET /timetables/me` | TEACHER | `TeacherLessonView` | ✓ No internal IDs, no personal data |
| `GET /timetables/my-stream` | STUDENT | `StudentLessonView` | ✓ No internal IDs, teacher name only |
| `GET /attendance/me` | STUDENT | `StudentAttendanceView` | ⚠️ Exposes **remarks** (admin notes), **updatedAt** (audit), internal IDs |
| `GET /attendance/stream` | ADMIN, TEACHER | `StudentAttendanceView` | ⚠️ Exposes internal IDs, remarks, updatedAt — acceptable for admin/teacher |
| `POST /attendance/batch` | ADMIN, TEACHER | `BatchResponse` → `StudentAttendanceView` | ⚠️ Same as above — acceptable for admin/teacher |

### A3.3 Issues Found

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| A3-1 | High | `AttendanceViews.java:21` | `StudentAttendanceView` exposes `remarks` to students via `myAttendance` — admin-only notes visible to students |
| A3-2 | Medium | `AttendanceViews.java:22` | `StudentAttendanceView` exposes `updatedAt` (audit data) to all roles including students |
| A3-3 | Medium | `AttendanceViews.java:13-14` | `StudentAttendanceView` exposes `id`, `studentId`, `streamId`, `timetableLessonId` — sequential IDs visible to students |
| A3-4 | Low | `TimetableViews.java:20-21` | `LessonView` exposes internal IDs — acceptable for admin-only endpoints |
| A3-5 | Medium | `TimetableService.java:498-499` | `myTeacherLessons` returns lessons from ALL published terms, not just current term |
| A3-6 | Medium | `TimetableService.java:508-509` | `myStudentLessons` returns lessons from ALL published terms, not just current term |

---

## A4. Information Leak Channels

### A4.1 Error Message Analysis

| Channel | File:Line | Risk | Details |
|---|---|---|---|
| `TimetableRuleException` handler | `GlobalExceptionHandler.java:92-95` | Medium | Returns `ex.getMessage()` and `ex.getErrorCode()` — messages like "Teacher not found" could enumerate resources |
| `AttendanceRuleException` handler | `GlobalExceptionHandler.java:97-100` | Medium | Same pattern |
| `DataIntegrityViolationException` | `GlobalExceptionHandler.java:192-233` | Low | Returns constraint-specific messages — may leak schema info |
| `ModuleAccessDeniedException` | `GlobalExceptionHandler.java:80-90` | Low | Logs schoolId, userId, module, errorCode — acceptable for audit trail |
| `IllegalArgumentException` | `GlobalExceptionHandler.java:178-183` | Low | Returns `ex.getMessage()` — could leak internal details |
| School membership oracle | `ModuleAccessAspect.java:79-82` | Medium | 404 when schoolId doesn't match vs 200 when it does — reveals school membership |
| Resource enumeration | `TimetableService.java:618-624` | Low | "Teacher not found", "Stream not found" messages could be used as oracle |

### A4.2 Issues Found

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| A4-1 | Medium | `GlobalExceptionHandler.java:92-95` | `TimetableRuleException` returns detailed messages — could enumerate resources |
| A4-2 | Medium | `GlobalExceptionHandler.java:97-100` | `AttendanceRuleException` returns detailed messages |
| A4-3 | Medium | `ModuleAccessAspect.java:79-82` | 404 vs 200 oracle for school membership |
| A4-4 | Low | `GlobalExceptionHandler.java:192-233` | Constraint-specific messages may leak schema info |
| A4-5 | Low | `ApiResponse.java:22` | `timestamp` uses `LocalDateTime.now()` without Clock — minor info leak about server time |

---

## A5. Authorization and Mass Assignment

### A5.1 @PreAuthorize Coverage

| Controller | Method | @PreAuthorize | Service-Level Check |
|---|---|---|---|
| `TimetableController` | All 16 methods | ✓ All have @PreAuthorize | `requireAdmin()` for mutations |
| `TermController` | `createTerm` | `hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')` | `requireAdmin()` |
| `TermController` | `getTerms` | `isAuthenticated()` | None (read-only) |
| `TermController` | `getTermById` | `isAuthenticated()` | None (read-only) |
| `StreamSubjectController` | All 5 methods | ✓ All have @PreAuthorize | `requireAdmin()` for mutations |
| `AttendanceController` | `recordBatch` | `hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or hasRole('TEACHER')` | `requireRecorder()` |
| `AttendanceController` | `streamAttendance` | Same as above | `requireRecorder()` |
| `AttendanceController` | `myAttendance` | `hasRole('STUDENT')` | Role check in service |

### A5.2 Mass Assignment Protection

- `fail-on-unknown-properties: true` in `application.yaml:6` ✓
- All endpoints use DTOs (records), not entities ✓
- No entity binding from requests ✓
- `schoolId` is resolved from principal, not accepted from request body for non-SUPER_ADMIN ✓

### A5.3 Issues Found

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| A5-1 | Low | `StreamSubjectController.java:22-24` | No class-level module annotation — relies on method-level only. Valid per ControllerAnnotationValidator but less defensive. |

---

## A6. Correctness and Integrity

### A6.1 Migration Constraints

| Constraint | Migration | Purpose | Status |
|---|---|---|---|
| `uk_timetable_school_term` | V21:110 | One timetable per school per term | ✓ |
| `uk_lesson_stream_slot` | V21:134 | Stream cannot have two lessons in same period | ✓ |
| `uk_lesson_teacher_slot` | V21:135 | Teacher cannot have two lessons in same period | ✓ |
| `uk_lesson_room_slot` | V21:136 | Room cannot be booked twice in same period | ✓ |
| `uk_attendance_school_student_date_scope` | V22:23-24 | One attendance record per student per date per scope | ✓ |
| `chk_attendance_type` | V22:25 | DAILY or PERIOD only | ✓ |
| `chk_attendance_status` | V22:26 | PRESENT, ABSENT, LATE, EXCUSED | ✓ |
| `chk_attendance_scope` | V22:27-29 | DAILY → no lesson, PERIOD → requires lesson | ✓ |
| `chk_timetable_status` | V21:111 | DRAFT or PUBLISHED | ✓ |
| `chk_lesson_day` | V21:137 | 1-7 (Monday-Sunday) | ✓ |
| `chk_period_times` | V21:35 | end_time > start_time | ✓ |
| `chk_period_type` | V21:36 | LESSON or BREAK | ✓ |

### A6.2 Locking Analysis

| Method | Locks Timetable? | Notes |
|---|---|---|
| `createLessons` | ✓ `findLocked` (line 332) | |
| `moveLesson` | ✗ No lock (line 400-403) | **Race condition with publish** |
| `deleteLesson` | ✗ No lock (line 429-432) | **Race condition with publish** |
| `publish` | ✓ `findLocked` (line 455) | |
| `unpublish` | ✓ `findLocked` (line 476) | |

### A6.3 Clock Usage

| Method | Uses Clock? | Notes |
|---|---|---|
| `TimetableService.audit()` | ✗ `LocalDateTime.now()` (line 684) | **Should use injected clock** |
| `TimetableService.requireWritableTerm()` | ✓ `LocalDate.now(schoolZone(schoolId))` (line 603) | Uses Clock via schoolZone |
| `AttendanceService.validateEditableDate()` | ✓ `LocalDate.now(clock.withZone(...))` (line 259) | |
| `ModuleAccessService.effectiveStatus()` | ✓ `LocalDate.now(clock.withZone(...))` (line 41) | |
| `ApiResponse.timestamp` | ✗ `LocalDateTime.now()` (line 22) | Minor — server time exposure |

### A6.4 Transaction Boundaries

| Method | Transactional? | Event Publishing |
|---|---|---|
| `publish` | ✓ `@Transactional` (line 451) | `TimetablePublishedEvent` fired inside transaction (line 468) — **should fire after commit** |

### A6.5 Issues Found

| ID | Severity | File:Line | Finding |
|---|---|---|---|
| A6-1 | Critical | `TimetableService.java:399-426` | `moveLesson` does not lock the timetable — race condition with `publish` |
| A6-2 | Critical | `TimetableService.java:428-439` | `deleteLesson` does not lock the timetable — race condition with `publish` |
| A6-3 | High | `TimetableService.java:684` | `audit()` uses `LocalDateTime.now()` instead of injected Clock |
| A6-4 | High | `TimetableService.java:468` | `TimetablePublishedEvent` fires inside transaction — should use `@TransactionalEventListener(AFTER_COMMIT)` |
| A6-5 | Medium | `TimetableService.java:498-499` | `myTeacherLessons` returns lessons from ALL published terms |
| A6-6 | Medium | `TimetableService.java:508-509` | `myStudentLessons` returns lessons from ALL published terms |
| A6-7 | Low | `ApiResponse.java:22` | `timestamp` uses `LocalDateTime.now()` without Clock |

---

## A7. Known Suspected Problems Verification

| # | Suspected Problem | Status | Evidence |
|---|---|---|---|
| 1 | `findByIdAndSchoolId` argument swap | **NOT REPRODUCED** | Code uses `findBySchoolIdAndId(schoolId, id)` consistently throughout |
| 2 | `createLessons` checks weekly limits against saved lessons only | **ALREADY FIXED** | Lines 357-358: checks `countForAssignment` + `batchAssignmentCounts` — includes batch-in-progress |
| 3 | `createLessons` never checks academic year match | **ALREADY FIXED** | Lines 348-350: checks `assignment.getAcademicYearId()` vs `term.getAcademicYearId()` |
| 4 | `assign` checks teacher weekly cap per item rather than per batch | **PARTLY TRUE** | Lines 260-266: uses `weeklyLessonDeltas` map to track batch-level accumulation ✓ |
| 5 | `assign` CREATED audit never runs because IDs exist after saveAll | **ALREADY FIXED** | Lines 290-295: checks `rows.get(i).getId() == null` to detect new records |
| 6 | Mutators don't lock the timetable that publish locks | **CONFIRMED** | `moveLesson` (line 399) and `deleteLesson` (line 428) use `findBySchoolIdAndId` without lock |
| 7 | SUPER_ADMIN gets 400 on move/delete/publish/unpublish | **NOT REPRODUCED** | `schoolId()` method handles SUPER_ADMIN with explicit param (line 660-663) |
| 8 | "My timetable" queries return lessons from every published term | **CONFIRMED** | `findPublishedTeacherLessons` and `findPublishedStreamLessons` have no term filter |
| 9 | Publish/unpublish have no state checks | **PARTLY TRUE** | `publish` checks DRAFT status (line 335) but `unpublish` does not check PUBLISHED status |
| 10 | Published event fires inside transaction | **CONFIRMED** | Line 468: `events.publishEvent()` inside `@Transactional` method |
| 11 | Inactive subjects/streams/periods/rooms throw not-found | **CONFIRMED** | `requirePeriod` (line 649) and `requireRoom` (line 652) filter by `isActive()` — inactive entities return 404 |

---

## A8. Test Gap Analysis

| Test Scenario | Exists? | File | Notes |
|---|---|---|---|
| Clock injection (TimetableService) | ✓ | `TimetableAttackTest.java:82-95` | ATTACK 4 |
| Clock injection (AttendanceService) | ✓ | `AttendanceAttackTest.java:94-106` | ATTACK 2 |
| Class level mismatch in assign | ✓ | `TimetableAttackTest.java:97-124` | ATTACK 5 |
| Inactive room in publish | ✓ | `TimetableAttackTest.java:126-160` | ATTACK 6 |
| Period break conversion | ✓ | `TimetableAttackTest.java:162-185` | ATTACK 7 |
| Term controller endpoints | ✓ | `TimetableAttackTest.java:187-193` | ATTACK 8 |
| TIMETABLE module check (PERIOD attendance) | ✓ | `AttendanceAttackTest.java:74-91` | ATTACK 1 |
| Future date attendance | ✓ | `AttendanceAttackTest.java:108-134` | ATTACK 3 |
| FREE tier daily attendance | ✓ | `AttendanceAttackTest.java:136-177` | ATTACK 4 |
| **Unauthenticated → 401** | ✗ | — | Missing |
| **Wrong role → 403** | ✗ | — | Missing |
| **Other school → 404** | ✗ | — | Missing |
| **FREE school → 403 on TIMETABLE** | ✗ | — | Missing |
| **Expired school → read-only** | ✗ | — | Missing |
| **Upgrade/downgrade → next request** | ✗ | — | Missing |
| **Tampered enabledModules** | ✗ | — | Missing |
| **Concurrency on slot uniqueness** | ✗ | — | Missing |
| **Publish vs lesson creation race** | ✗ | — | Missing |
| **Draft invisibility to students/teachers** | ✗ | — | Missing |
| **Student A vs Student B isolation** | ✗ | — | Missing |
| **Teacher A vs Teacher B register isolation** | ✗ | — | Missing |

**Summary**: 9 existing tests, 12 missing test scenarios.

---

## A9. Findings Table (Ranked by Severity)

| ID | Severity | File:Line | Evidence | Exploit Scenario | Proposed Fix | Test Name |
|---|---|---|---|---|---|---|
| **A6-1** | Critical | `TimetableService.java:399-426` | `moveLesson` uses `findBySchoolIdAndId` without PESSIMISTIC_WRITE lock | Two concurrent requests: one moves a lesson, another publishes — unique constraint violation returns 500 | Use `timetableRepository.findLocked()` | `moveLessonLocksTimetable` |
| **A6-2** | Critical | `TimetableService.java:428-439` | `deleteLesson` uses `findBySchoolIdAndId` without PESSIMISTIC_WRITE lock | Two concurrent requests: one deletes a lesson, another publishes — inconsistent state | Use `timetableRepository.findLocked()` | `deleteLessonLocksTimetable` |
| **A3-1** | High | `AttendanceViews.java:21` | `StudentAttendanceView` includes `remarks` field | Student calls `GET /attendance/me` and sees admin remarks about other students' attendance | Create `MyStudentAttendanceView` without remarks | `studentCannotSeeRemarks` |
| **A6-3** | High | `TimetableService.java:684` | `audit()` uses `LocalDateTime.now()` | Audit timestamps are non-deterministic, cannot be tested reliably | Use `LocalDateTime.now(clock)` | `auditUsesClock` |
| **A6-4** | High | `TimetableService.java:468` | `events.publishEvent()` inside `@Transactional` | Event listener runs before commit — if listener fails, transaction rolls back unnecessarily | Use `@TransactionalEventListener(phase = AFTER_COMMIT)` | `publishEventFiresAfterCommit` |
| **A2-1** | Medium | `TimetableLessonRepository.java:53-54` | JPQL joins Timetable but doesn't check `t.schoolId` | Theoretical: if lesson.schoolId differs from timetable.schoolId, cross-tenant data leak | Add `and t.schoolId = :schoolId` | `publishedTeacherLessonsTenantIsolated` |
| **A2-2** | Medium | `TimetableLessonRepository.java:56-57` | Same as A2-1 | Same as A2-1 | Add `and t.schoolId = :schoolId` | `publishedStreamLessonsTenantIsolated` |
| **A3-2** | Medium | `AttendanceViews.java:22` | `StudentAttendanceView` includes `updatedAt` | Student sees audit timestamp — minor info leak | Remove from student view | `studentCannotSeeUpdatedAt` |
| **A3-5** | Medium | `TimetableService.java:498-499` | `findPublishedTeacherLessons` returns ALL published terms | Teacher sees lessons from past terms mixed with current term | Add term filtering or use current term | `teacherSeesCurrentTermOnly` |
| **A3-6** | Medium | `TimetableService.java:508-509` | `findPublishedStreamLessons` returns ALL published terms | Student sees lessons from past terms | Add term filtering | `studentSeesCurrentTermOnly` |
| **A4-1** | Medium | `GlobalExceptionHandler.java:92-95` | Returns `ex.getMessage()` in response | Attacker enumerates resources by checking error messages | Use generic messages for NOT_FOUND | `errorMessagesDoNotEnumerate` |
| **A4-2** | Medium | `GlobalExceptionHandler.java:97-100` | Same as A4-1 | Same as A4-1 | Same as A4-1 | `attendanceErrorMessagesDoNotEnumerate` |
| **A4-3** | Medium | `ModuleAccessAspect.java:79-82` | 404 when schoolId doesn't match | Attacker determines school membership by 404 vs 200 | Return consistent 403 | `schoolMembershipNotRevealed` |
| **A1-1** | Low | `ModuleAccessAspect.java:106-108` | SUPER_ADMIN bypasses module checks | SUPER_ADMIN can access TIMETABLE for FREE school | Document as intentional | N/A |
| **A1-2** | Low | `ModuleAccessAspect.java:79-82` | Same as A4-3 | Same as A4-3 | Same as A4-3 | Same as A4-3 |
| **A3-3** | Medium | `AttendanceViews.java:13-14` | Sequential IDs exposed to students | Student can enumerate other students by ID | Remove IDs from student view | `studentViewNoInternalIds` |
| **A3-4** | Low | `TimetableViews.java:20-21` | `LessonView` exposes internal IDs | Admin can see internal IDs — acceptable for admin-only endpoints | Acceptable as-is | N/A |
| **A4-4** | Low | `GlobalExceptionHandler.java:192-233` | Constraint-specific messages | Attacker learns DB schema from error messages | Use generic messages | N/A |
| **A4-5** | Low | `ApiResponse.java:22` | `LocalDateTime.now()` without Clock | Server time exposure — minor | Use Clock | N/A |
| **A5-1** | Low | `StreamSubjectController.java:22-24` | No class-level annotation | Less defensive — but method-level annotations work | Add class-level annotation or document | N/A |
| **A6-7** | Low | `ApiResponse.java:22` | Same as A4-5 | Same as A4-5 | Use Clock | N/A |

---

## Decisions Needed Before Phase B

1. **SUPER_ADMIN module bypass** (A1-1): Should SUPER_ADMIN bypass module checks for all schools, or should they be subject to the same module gates? **Recommendation**: Keep as-is (by design for multi-tenant admin).

2. **Remarks visibility** (A3-1): Should `remarks` in `StudentAttendanceView` be hidden from students? **Recommendation**: Yes — create separate view for student self-service.

3. **Term filtering** (A3-5, A3-6): Should `myTeacherLessons` / `myStudentLessons` filter to the current term only? **Recommendation**: Yes — return only the current active term's lessons.

4. **Pessimistic locking** (A6-1, A6-2): Should `moveLesson` and `deleteLesson` acquire pessimistic locks like `publish`? **Recommendation**: Yes — prevent race conditions.

5. **Event timing** (A6-4): Should `TimetablePublishedEvent` fire after commit? **Recommendation**: Yes — use `@TransactionalEventListener(AFTER_COMMIT)`.

6. **Error message standardization** (A4-1, A4-2): Should NOT_FOUND messages be generic to prevent resource enumeration? **Recommendation**: Yes — use "Resource not found" for all NOT_FOUND cases.

7. **School membership oracle** (A4-3): Should the 404 vs 200 distinction be removed? **Recommendation**: Return 403 with generic message when schoolId doesn't match.