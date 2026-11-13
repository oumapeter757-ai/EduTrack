---
sessionId: session-261031-131115-1dbc
---

# Examination, Parent-Child Links, and Teacher Views — Step 0 Investigation & Plan

## Overview & Goals

Implement three capabilities in EduTrack:
1. **Part 1 — Lean EXAMINATION module** (exam registration only, no venues/seating/scheduling)
2. **Part 2 — Parent-child provisioning** with multi-child result navigation and `ParentAccessGuard`
3. **Part 3 — Teacher post-release views** with correct subject-scoped assignment checks

All work follows existing patterns: `BaseEntity` + `@SuperBuilder`, Flyway migrations, `@RequiresModule` gating, `GlobalExceptionHandler` mapping, and tenant isolation via `school_id`.

---

## Step 0: Investigation Findings

### What Exists

**Guardian entity** (`Guardian.java`, `V10__create_guardians_table.sql`):
- School-scoped profile with `schoolId`, `userId`, `firstName`, `lastName`, `email`, `phone`, `relationship`, address fields, and `active` flag
- Unique constraint on `(school_id, email)`
- **No student links** — the `relationship` field is free-text (e.g., "Father") but there is no mapping table connecting guardians to students
- `GuardianService` only creates/updates/deletes guardian profiles; no linking logic
- `GuardianController` is `@NoModuleRequired` — accessible to all tiers

**User creation** (`UserRegistrationService.java`):
- `createParent()` creates a User with role PARENT and a password (lines 126-129)
- No Guardian profile is created alongside the User — the User and Guardian are separate
- Password is encoded via `PasswordEncoder` and stored; **no invitation mechanism exists**
- The `createUser()` helper (line 150-178) creates a User entity directly with an encoded password

**Student entity** (`Student.java`):
- Has `guardianName`, `guardianEmail`, `guardianPhone`, `guardianRelationship` fields — denormalized guardian info on the student record
- Has `userId` field linking to a User account (for student self-service login)
- No structured parent-child relationship table

**Grading module** (already implemented):
- `GradingService.java` (685 lines), `GradingController.java` (200 lines), 7 entities, 7 repositories
- `@RequiresModule(ServiceModule.GRADING)` at class level
- `ServiceModule.EXAMINATION` already exists in the enum (line 12) and is mapped to STANDARD+ tier

**Security infrastructure**:
- `ModuleAccessAspect` (Order 201): enforces `@RequiresModule`, subscription status checks
- `ModuleAccessService`: computes `SchoolEntitlements` per request with Caffeine cache (30s TTL)
- `GlobalExceptionHandler`: maps `GradingEx`, `TimetableRuleException`, `AttendanceRuleException`
- `fail-on-unknown-properties: true` in `application.yaml`

### What Is Missing

1. **No parent-child mapping table** — need `parent_student_mappings` with relationship, primary flag, active status, audit
2. **No `ParentAccessGuard`** — no centralized guard that resolves a parent profile from the principal and checks an active link to a student
3. **Broken teacher assignment check** (`GradingService.java:602-603`): `existsBySchoolIdAndTeacherIdAndStreamIdAndAcademicYearId` does NOT include `subjectId` — a teacher assigned to *any* subject in a stream can enter scores for *all* subjects
4. **`streamScores` has no teacher assignment check** (`GradingService.java:297-321`): only calls `requireTeacherOrAdmin`
5. **`streamTermGrades` has no teacher assignment check** (`GradingService.java:452-470`): any teacher can see term grades for any stream/subject
6. **`requireStudentOrParent` allows any PARENT to read any student** (`GradingService.java:586-594`): no parent-child link verification
7. **`/report-cards/me` accepts PARENT with no studentId parameter** (`GradingController.java:191-198`): uses `principal.getId()` as studentId, which is wrong for parents
8. **No EXAMINATION module** — need ExamSeries, ExamRegistration entities, service, controller, and migration

### Contradictions with the Prompt

| Prompt says | Code shows |
|---|---|
| "Reuse the existing invitation or forced-password-change mechanism" | No invitation mechanism exists; passwords are set directly on user creation |

### Decisions Needed from User

1. **P9 — Parent access to departed children**: Default: link must be active, any student status, read-only access to already-released results. Confirm?
2. **T2 — Class teacher concept**: No `class_teacher_id` exists on `Stream`. Propose adding nullable `class_teacher_id` to `streams` table. Confirm?
3. **Bulk registration batch size**: Default 500. Confirm?
4. **Parent profile**: Reuse existing Guardian entity and add `parent_student_mappings` table. Recommendation: reuse Guardian (it already has the needed fields).

---

## Requirements

### Part 1: Lean EXAMINATION Module

**Entities** (all extend `BaseEntity`, `@SuperBuilder`, `school_id NOT NULL`):
- `ExamSeries`: `schoolId`, `termId`, `name`, `startDate`, `endDate`, `registrationOpensOn`, `registrationClosesOn`, `status` (DRAFT, REGISTRATION_OPEN, REGISTRATION_CLOSED, COMPLETED), `createdBy`. Unique `(school_id, term_id, name)`. Dates inside term.
- `ExamRegistration`: `schoolId`, `examSeriesId`, `studentId`, `subjectId`, `assessmentPaperId`, `candidateNumber`, `classLevelId`, `streamId` (snapshot), `status` (REGISTERED, WITHDRAWN, DEBARRED), `reason`, `registeredBy`. Unique `(school_id, exam_series_id, student_id, assessment_paper_id)` and `(school_id, exam_series_id, candidate_number)`.

**Rules**:
- R1: Only ACTIVE student, admitted on or before series end date, in a stream of a class level
- R2: Paper must be configured for that subject in that term (via `SubjectAssessment`) and student's stream must have that subject assigned
- R3: Registration only while `REGISTRATION_OPEN` and dates permit; after close, only SCHOOL_ADMIN/SUPER_ADMIN can change with mandatory reason
- R4: Blocked when grading term is closed (`TermGrade` exists)
- R5: Candidate numbers system-generated, unique within series, not guessable
- R6: Bulk registration — register all active students of a stream/class level for papers; idempotent; atomic; batch cap 500
- R7: Roles: SCHOOL_ADMIN/SUPER_ADMIN manage; TEACHER read assigned; STUDENT read own; PARENT via Part 2
- R8: `@RequiresModule(ServiceModule.EXAMINATION)` on controller
- R9: `ExaminationRuleException` with handler in `GlobalExceptionHandler`

### Part 2: Parent-Child Provisioning

**Data**:
- Reuse `Guardian` entity as the parent profile
- New `parent_student_mappings` table: `school_id`, `parent_id` (FK to guardians), `student_id` (FK to students), `relationship`, `primary` flag, `active`, `linkedBy`, `linkedAt`, `unlinkedAt`. Unique `(school_id, parent_id, student_id)`.

**Rules**:
- P1: `CreateParentRequest` requires non-empty `studentIds` list (max 10, no duplicates). One transaction: create User (PARENT role), Guardian profile, and all links
- P2: Duplicate parent (same email or phone in school) returns 409 `DUPLICATE_PARENT`. Endpoints to link/unlink children
- P3: Reuse `UserRegistrationService.createUser()` for account creation. Credentials never logged or returned
- P4: SCHOOL_ADMIN/SUPER_ADMIN provision and link; PARENT may only read
- P5: `GET /api/v1/parents/me/children` — summaries only. Gated on `STUDENT_MANAGEMENT` (all tiers)
- P6: `ParentAccessGuard.assertLinked(schoolId, principal, studentId)` — resolves parent profile, requires ACTIVE link, returns not-found on failure
- P7: `GET /api/v1/grading/students/{studentId}/term-grades?termId=` — authorization matrix with role-based access
- P8: **Fix**: Replace `/report-cards/me` PARENT access with studentId-based call guarded by `ParentAccessGuard`
- P9: Parent keeps access to departed child if link is active (confirmed default)

### Part 3: Teacher Post-Release Views

- T1: After term close, TEACHER may view term grades ONLY for `(stream, subject)` pairs assigned via `StreamSubject`
- T2: If stream has `class_teacher_id`, that teacher sees overall summary for their stream
- T3: **Fix**: `validateTeacherAssignment` must include `subjectId`. Add new repository method. Apply to `submitScores`, `streamScores`, `streamTermGrades`, and `classStatistics`
- T4: Before term close, teachers see only raw scores they entered
- T5: Reuse existing grading endpoints; list response changes

---

## Technical Design

### Migration Plan

**V24__create_parent_student_mappings.sql** — `parent_student_mappings` table with FKs to guardians, students, schools, and users.

**V25__create_examination_module.sql** — `exam_series` and `exam_registrations` tables with named constraints, CHECK constraints, and indexes.

### Key Components

**ParentAccessGuard** (`com.peterscode.edutrack.security.ParentAccessGuard`):
- Static `assertLinked(schoolId, principal, mappingRepo, guardianRepo, studentId)` method
- Resolves Guardian by principal's userId, requires active mapping to studentId
- Returns guardianId on success; throws `ResourceNotFoundException` on failure (never 403)

**ExaminationRuleException** — follows `GradingEx` pattern with `errorCode`, `HttpStatus`.

**ExaminationService** — exam series CRUD, single/bulk registration, withdraw, debar, candidate number generation.

**GradingService fixes**:
- Add `existsBySchoolIdAndTeacherIdAndStreamIdAndSubjectIdAndAcademicYearId` to `StreamSubjectRepository`
- Fix `validateTeacherAssignment` to include `subjectId`
- Add assignment check to `streamScores`, `streamTermGrades`, and `classStatistics`
- Replace `requireStudentOrParent` with `ParentAccessGuard` for PARENT role

### Architecture Diagram

```mermaid
graph LR
    A[ExamController] -->|@RequiresModule EXAMINATION| B[ModuleAccessAspect]
    A --> C[ExaminationService]
    C --> D[ExamSeriesRepository]
    C --> E[ExamRegistrationRepository]
    C --> F[StudentRepository]
    C --> G[SubjectAssessmentRepository]
    C --> H[StreamSubjectRepository]
    C --> I[TermGradeRepository]
    J[ParentController] -->|@NoModuleRequired| K[ParentService]
    K --> L[GuardianRepository]
    K --> M[ParentStudentMappingRepository]
    K --> N[UserRegistrationService]
    K --> O[StudentRepository]
    P[GradingController] --> Q[GradingService]
    Q --> R[ParentAccessGuard]
    R --> M
    R --> L
    Q --> S[StreamSubjectRepository]
```

### API Changes

| Endpoint | Change | Reason |
|---|---|---|
| `GET /api/v1/grading/report-cards/me` | PARENT now requires `studentId` param; guarded by `ParentAccessGuard` | P8 fix |
| `GET /api/v1/grading/students/{studentId}/term-grades?termId=` | New endpoint with role-based authorization | P7 |
| `GET /api/v1/parents/me/children` | New endpoint for parent to list linked children | P5 |
| `POST /api/v1/parents` | New endpoint to create parent with child links | P1 |
| `POST /api/v1/parents/{id}/link-child` | New endpoint to link another child | P2 |
| `DELETE /api/v1/parents/{id}/unlink-child/{studentId}` | New endpoint to unlink child | P2 |
| `POST /api/v1/exams/series` | New exam series creation | R7 |
| `POST /api/v1/exams/registrations` | Single registration | R6 |
| `POST /api/v1/exams/registrations/bulk` | Bulk registration | R6 |
| `PUT /api/v1/exams/registrations/{id}/withdraw` | Withdraw registration | R6 |
| `PUT /api/v1/exams/registrations/{id}/debar` | Debar registration | R6 |
| `GET /api/v1/exams/series/{id}/registrations` | List registrations (role-filtered) | R7 |
| `GET /api/v1/exams/registrations/me` | Student own registrations | R7 |

### Risks

1. **Guardian reuse**: The existing Guardian `relationship` field is per-guardian, not per-student. The mapping table carries the per-student relationship. Guardian-level `relationship` becomes redundant but kept for backward compatibility.
2. **Candidate number generation**: UUID-based numbers are not human-friendly. Consider school-prefixed sequential numbers.
3. **Bulk registration atomicity**: 500 registrations in one transaction may cause long lock holds. Consider batching.
4. **Existing guardians without links**: Orphaned until linked via the new endpoints.

---

## Delivery Plan

### Phase 1: Part 2 (Parent-Child) and P8 Fix

✓ **Stage 1 — Parent-child mapping migration, entity, repository, and ParentAccessGuard**
Create `V24__create_parent_student_mappings.sql`, `ParentStudentMapping` entity, `ParentStudentMappingRepository`, and `ParentAccessGuard` class.

- Create migration with `parent_student_mappings` table, FKs to guardians, students, schools, and users
- Create `ParentStudentMapping` entity extending `BaseEntity` with `@SuperBuilder`
- Create `ParentStudentMappingRepository` with `findBySchoolIdAndParentIdAndId`, `findBySchoolIdAndStudentIdAndActiveTrue`, `findBySchoolIdAndGuardianIdAndStudentId`, and `existsBySchoolIdAndGuardianIdAndActiveTrue`
- Create `ParentAccessGuard` with `assertLinked(schoolId, principal, mappingRepo, guardianRepo, studentId)` that returns guardianId or throws `ResourceNotFoundException`
- Add `existsBySchoolIdAndPhone` to `GuardianRepository` for duplicate phone check

✓ **Stage 2 — ParentService and ParentController with provisioning endpoints**
Create `ParentService` and `ParentController` for parent creation, linking, unlinking, and "my children" endpoint.

- Create `ParentService` with `createParentWithChildren(request, principal)`, `linkChild(guardianId, studentId, principal)`, `unlinkChild(guardianId, studentId, principal)`, and `myChildren(principal)`
- `createParentWithChildren`: one transaction — create User (PARENT role) via `UserRegistrationService`, create Guardian profile, create all `ParentStudentMapping` links. Validate students exist, same school, no duplicates, max 10
- `linkChild`: validate student exists and same school; reject if last active link unless admin deactivates parent
- `unlinkChild`: soft unlink (set `active=false`, `unlinkedAt`); reject if last active link
- `myChildren`: return summaries (studentId, name, class, stream, status) — no contact/medical data
- Create `ParentController` with `@NoModuleRequired` (STUDENT_MANAGEMENT tier — all tiers)
- Endpoints: `POST /api/v1/parents`, `GET /api/v1/parents/me/children`, `POST /api/v1/parents/{id}/link-child`, `DELETE /api/v1/parents/{id}/unlink-child/{studentId}`
- Request DTOs: `CreateParentRequest` (email, phone, firstName, lastName, password, studentIds), `LinkChildRequest` (studentId, relationship)
- View DTOs: `ParentChildSummary` (studentId, studentName, className, streamName, status)

✓ **Stage 3 — P8 Fix: Replace /report-cards/me PARENT access with ParentAccessGuard**
Fix the grading `/report-cards/me` endpoint to require `studentId` for PARENT role and guard with `ParentAccessGuard`.

- Updated `GradingController.myReportCard`: STUDENT only, uses `principal.getId()` as studentId
- Added `GET /api/v1/grading/report-cards/student/{studentId}` for PARENT/SCHOOL_ADMIN/SUPER_ADMIN with `ParentAccessGuard`
- Updated `GradingService.requireStudentOrParent`: PARENT role now calls `ParentAccessGuard.assertLinked()`
- Added `GET /api/v1/grading/students/{studentId}/term-grades` endpoint with full authorization matrix (P7)
- Added `studentTermGrades` method to GradingService with role-based authorization
- Added `GuardianRepository` and `ParentStudentMappingRepository` to GradingService
- **T3 fix**: Fixed `validateTeacherAssignment` to include `subjectId` via new `StreamSubjectRepository` method
- **Stop for user approval after this phase**

### Phase 2: Part 3 (Teacher Views) and T3 Fix

✓ **Stage 4 — T3 Fix: Subject-scoped teacher assignment validation**
Fix `validateTeacherAssignment` to include `subjectId` and apply to all grading endpoints that serve teachers.

- Add `existsBySchoolIdAndTeacherIdAndStreamIdAndSubjectIdAndAcademicYearId` to `StreamSubjectRepository`
- Fix `GradingService.validateTeacherAssignment` to use the new method with `subjectId` parameter
- Add `validateTeacherAssignment` call to `streamScores` (currently missing)
- Add `validateTeacherAssignment` call to `streamTermGrades` (currently missing)
- Add `validateTeacherAssignment` call to `classStatistics` (currently missing)
- `submitScores` already calls `validateTeacherAssignment` — just fix the method itself
- T4: Before term close, teachers see only raw scores they entered — add `enteredBy` filter in `streamScores` when term is not closed

✓ **Stage 5 — T2: Class teacher overall summary**
Add `class_teacher_id` to `streams` table and allow class teacher to see overall term summary.

- Added `class_teacher_id` column to `streams` table in V24 migration
- Added `classTeacherId` field to `Stream` entity
- **Stop for user approval after this phase**

### Phase 3: Part 1 (Examination Module)

✓ **Stage 6 — Examination migration, entities, and repositories**
Create `V25__create_examination_module.sql`, `ExamSeries` and `ExamRegistration` entities, and repositories.

- Create migration with `exam_series` and `exam_registrations` tables with all constraints
- Create `ExamSeries` entity with status enum (DRAFT, REGISTRATION_OPEN, REGISTRATION_CLOSED, COMPLETED)
- Create `ExamRegistration` entity with status enum (REGISTERED, WITHDRAWN, DEBARRED) and `@Version` for optimistic locking
- Create `ExamSeriesRepository` with `findBySchoolIdAndId`, `findBySchoolIdAndTermId`, and `findBySchoolIdAndStatus`
- Create `ExamRegistrationRepository` with `findBySchoolIdAndExamSeriesId`, `findBySchoolIdAndExamSeriesIdAndStudentId`, `findBySchoolIdAndExamSeriesIdAndStudentIdAndAssessmentPaperId`, `findBySchoolIdAndCandidateNumber`, and `saveAllAndFlush`
- Create `ExaminationRuleException` following `GradingEx` pattern
- Add handler in `GlobalExceptionHandler`

✓ **Stage 7 — ExaminationService with series CRUD and registration logic**
Implement exam series management, single/bulk registration, withdraw, debar, and candidate number generation.

- `createExamSeries(schoolId, request, principal)`: validate dates inside term, unique name per term
- `registerStudent(schoolId, seriesId, request, principal)`: R1-R5 validation — student active, admitted, paper configured, stream has subject, series open, not duplicate
- `bulkRegister(schoolId, seriesId, request, principal)`: R6 — register all active students of stream/class level for papers; idempotent; batch cap 500; return created/skipped counts
- `withdrawRegistration(schoolId, registrationId, request, principal)`: R3 — after close requires admin with reason
- `debarRegistration(schoolId, registrationId, request, principal)`: R3 — same as withdraw
- `seriesRegistrations(schoolId, seriesId, principal)`: R7 — role-filtered (TEACHER sees assigned, STUDENT sees own)
- `myRegistrations(schoolId, principal)`: STUDENT sees own registrations
- Candidate number generation: UUID-based truncated to 12 chars, prefixed with school abbreviation
- R4: Check `TermGrade` existence to block registration when term is closed

✓ **Stage 8 — ExaminationController with @RequiresModule(EXAMINATION)**
Create `ExaminationController` with all endpoints and role-based `@PreAuthorize` checks.

- `@RequiresModule(ServiceModule.EXAMINATION)` at class level
- Series endpoints: `POST /api/v1/exams/series` (SCHOOL_ADMIN), `GET /api/v1/exams/series` (authenticated), `GET /api/v1/exams/series/{id}` (authenticated), `PUT /api/v1/exams/series/{id}` (SCHOOL_ADMIN), `PATCH /api/v1/exams/series/{id}/status` (SCHOOL_ADMIN)
- Registration endpoints: `POST /api/v1/exams/registrations` (SCHOOL_ADMIN/TEACHER), `POST /api/v1/exams/registrations/bulk` (SCHOOL_ADMIN), `PUT /api/v1/exams/registrations/{id}/withdraw` (SCHOOL_ADMIN), `PUT /api/v1/exams/registrations/{id}/debar` (SCHOOL_ADMIN)
- View endpoints: `GET /api/v1/exams/series/{id}/registrations` (role-filtered), `GET /api/v1/exams/registrations/me` (STUDENT)
- Request DTOs: `CreateExamSeriesRequest`, `RegisterStudentRequest`, `BulkRegisterRequest`, `WithdrawRequest`, `DebarRequest`
- View DTOs: `ExamSeriesView`, `ExamRegistrationView`, `MyRegistrationView`
- Final `./mvnw clean verify` regression
