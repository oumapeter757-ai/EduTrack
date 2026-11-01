---
sessionId: session-261101-124611-ccse
---

# Requirements

## Overview & Goals

Implement a Learning Management System (LMS) module for EduTrack — a multi-tenant school management SaaS. The module provides courses, topics, learning resources, assignments, submissions, marking, feedback, announcements, student/parent read views, secure file storage with per-school quota, and comprehensive audit logging.

## Scope

### In Scope
- Courses (tied to stream-subject), topics, learning resources (file/link/text)
- Assignments with submission windows, late policies, and resubmission
- Submissions with concurrency-safe double-submit protection
- Marking with score validation, feedback, and release control
- Announcements per course
- Student views: my courses, my assignments, my released scores
- Parent views: linked child's courses, assignments, released scores
- Secure file storage: upload, authorized download, content validation, quota
- Per-school storage quota with concurrent upload protection
- Admin moderation: hide/remove content with mandatory reason
- Audit logging for all actions
- Event publishing (course published, assignment published, submission marked/released)
- Subscription gating: LMS only available on ENTERPRISE tier

### Out of Scope (reported, not built)
- Quizzes, discussion forums, student chat
- Video hosting or live streaming
- Grading integration with existing grading module
- Peer review, plagiarism checks, badges
- Offline sync
- Notifications (events published only, no listeners built)
- Virus scanner integration (hook provided, no-op default)
- Elective subjects (courses are per stream-subject)

## User Stories

- As a **TEACHER**, I want to create courses for my assigned stream-subjects so students can access learning materials.
- As a **TEACHER**, I want to create assignments with due dates and late policies so students know when work is due.
- As a **STUDENT**, I want to submit assignments and resubmit before the due date so I can improve my work.
- As a **STUDENT**, I want to see my released scores and feedback so I know how I performed.
- As a **PARENT**, I want to view my linked child's assignments and released scores so I can track progress.
- As a **SCHOOL_ADMIN**, I want to moderate content and hide inappropriate materials with an audit trail.
- As a **SCHOOL_ADMIN**, I want to monitor storage usage so I can manage the school's file quota.

## Functional Requirements

### Authorization (Rules M1-M8)
- **M1**: Only the teacher assigned to the course's stream-subject (teacher + stream + subject) creates/edits content and marks work. SCHOOL_ADMIN (and SUPER_ADMIN with explicit school_id) may view everything and hide/remove content with a mandatory reason, audited.
- **M2**: Students see only PUBLISHED content from their own stream's courses, while ACTIVE and enrolled. Other streams/schools return 404.
- **M3**: Submissions visible only to the student, course teacher, and admins. Never to classmates.
- **M4**: Submission window enforced (opens_at to due_at). Late policy: BLOCK rejects, ALLOW_FLAGGED accepts with late flag. CLOSED/unpublished assignments reject submissions. Resubmission replaces text and attachments. Concurrent double-submit produces exactly one row (unique constraint + 409 on conflict).
- **M5**: Score between 0 and maxScore (BigDecimal, scale 2). Feedback length-limited. Students see score/feedback only after released=true. Change after release needs reason and is audited. Stale update returns 409 VERSION_CONFLICT.
- **M6**: PARENT reads only linked child's published assignments, due dates, and RELEASED scores via ParentAccessGuard. Unlinked child returns 404.
- **M7**: Default-deny: every role not explicitly authorized (including LIBRARIAN) gets 403 on LMS endpoints. Unauthenticated gets 401.
- **M8**: @RequiresModule(ServiceModule.LMS) on every controller. Below ENTERPRISE: 403 MODULE_NOT_IN_PLAN. After grace period: reads work, writes return 402 SUBSCRIPTION_EXPIRED.

### File Storage (Rules S1-S8)
- **S1**: FileStorageService interface with local-disk implementation. Configured by mandatory `edutrack.storage.base-path` (validated at startup: absolute, no '..', outside project/static resources, exists/creatable, writable, not world-readable). S3-ready interface.
- **S2**: Allowed types: explicit configurable whitelist (default: pdf, doc, docx, ppt, pptx, xls, xlsx, txt, png, jpg, jpeg). Reject executables, scripts, html, svg, archives. Validate by content (magic numbers), not just extension. Reject mismatches and double extensions. Reject empty files.
- **S3**: Max file size: 10 MB (configurable). Per-school quota: 1 GB (configurable). Check against SchoolStorageUsage under row lock. Failed upload must not leak usage or leave orphan files.
- **S4**: Storage keys: random, unguessable (UUID). School id in path. Original filename sanitized (reject path separators, null bytes, traversal; bounded length). SHA-256 checksum stored.
- **S5**: Download is authorized, authenticated endpoint. Resolve attachment, check M1-M3 and M6. Response headers: Content-Disposition: attachment, X-Content-Type-Options: nosniff. Stream file (no full memory load). Support HEAD.
- **S6**: FileScanner interface with no-op default (scanStatus stays UNSCANNED). Resources/submissions with REJECTED files not served.
- **S7**: Cleanup job: removes DELETED files and orphans after configurable grace period. Idempotent, per-school with limits. `@Scheduled` fixed-rate (default 24h, configurable).
- **S8**: Multipart limits in configuration consistent with application limits. Oversize returns 413.

### Other Requirements
- **M9**: Events published after commit in event package. Future listeners check entitlement and skip silently.
- **M10**: Every list endpoint is pageable (default 20, max 100). Sort whitelist (invalid sort returns 400). No N+1 queries.
- **M11**: Reject unknown JSON properties on every DTO (@JsonIgnoreProperties(ignoreUnknown=false)). Validate every request (@Valid, sizes, ranges). No JPA entities in responses. No personal data in logs.

## Non-Functional Requirements
- Test-first approach with Testcontainers on MySQL (real Flyway migrations)
- H2 only for plain unit tests
- No 500 errors from business logic — specific error codes mapped to 400, 403, 404, 409, 413, or 402
- Append-only audit (no update or delete methods)
- Optimistic locking (@Version) on mutable entities
- Clock injection for time-sensitive logic (testable)
- Small commits, never edit existing migrations

# Technical Design

## Current Implementation

### What Exists (to be reused)
- **`ServiceModule.LMS`** — already in the enum at `com.peterscode.edutrack.school.enums.ServiceModule`, included in ENTERPRISE only via `getDefaultModulesForTier()`
- **`@RequiresModule` / `ModuleAccessAspect`** (Order 201) — enforces module access at controller level. Evaluation order: authenticated → role → tenant → module/subscription
- **`ModuleAccessService`** — manages entitlements with Caffeine cache (30s TTL), grace period logic (configurable `edutrack.subscription.grace-days`, default 14). During grace: reads work, writes return 402
- **`ParentAccessGuard`** — static utility for parent-child verification. Returns 404 (not 403) when not linked
- **`UserPrincipal`** — holds id, email, schoolId, role, active. SUPER_ADMIN has no schoolId
- **`SecurityUtils`** — getCurrentPrincipal(), getCurrentSchoolId(), getCurrentUserId()
- **`JwtAuthenticationFilter`** — JWT-based authentication, stateless sessions
- **`Clock` bean** — in `ApplicationConfig`, `Clock.systemUTC()`. Injected in AttendanceService, GradingService, LibraryBorrowingService, TimetableService
- **`GlobalExceptionHandler`** — handles module-specific exceptions (TimetableRuleException, AttendanceRuleException, GradingEx, LibraryRuleException). Will add LmsRuleException handler
- **`ApiResponse` / `PageResponse`** — standard response wrappers
- **`BaseEntity`** — @MappedSuperclass with id, createdAt, updatedAt. Uses @SuperBuilder
- **Library module** — best reference for conventions:
  - `LibraryRuleException(errorCode, HttpStatus, message)` pattern
  - `LibraryRequests` / `LibraryViews` DTO classes with record-based DTOs
  - `@JsonIgnoreProperties(ignoreUnknown = false)` on every DTO record
  - `LibraryAudit` / `LibraryAuditService` — append-only audit with school_id, entity_type, entity_id, action, actor_id, old_value, new_value, reason, occurred_at
  - Multiple services per domain (LibraryCatalogueService, LibraryBorrowingService, LibraryFineService, LibraryViewsService)
  - Controllers with `@RequiresModule(ServiceModule.LIBRARY)` and `@PreAuthorize` per endpoint
- **`StreamSubject`** — entity with schoolId, streamId, subjectId, academicYearId, teacherId. The course anchor
- **`Student`** — entity with schoolId, streamId, status (ACTIVE/...), active, admissionDate
- **`Staff`** — entity with schoolId, userId, status, active
- **`Role` enum** — SUPER_ADMIN, SCHOOL_ADMIN, TEACHER, STUDENT, PARENT, LIBRARIAN
- **Event pattern** — `TimetablePublishedEvent` (record) + `TimetablePublishedEventListener` with `@TransactionalEventListener` (AFTER_COMMIT)
- **`@Version`** — used on Loan, StudentScore, Timetable, BookCopy, LibraryBorrowerAccount, LibraryFine, ExamRegistration, StudentAttendance for optimistic locking
- **Flyway migrations** — V1 through V30. Latest is V30 (library borrowing). Next: V31
- **Test setup** — H2 for unit tests in `src/test/resources/application.yaml`. No Testcontainers yet
- **Jackson** — `FAIL_ON_UNKNOWN_PROPERTIES` enabled globally in `ApplicationConfig.objectMapper()` and `application.yaml`

### What is Missing
- No file upload code exists anywhere in the project
- No Testcontainers dependency — will add for integration tests
- No LMS entities, services, controllers, or migrations

## Key Decisions

1. **Storage backend**: No default path. `edutrack.storage.base-path` must be set explicitly. App fails to start if missing or invalid. Validated at startup: absolute path, no '..', outside project directory and static resources, exists or creatable, writable, not world-readable (700 or 750). Configurable minimum free disk space check.
2. **File size**: 10 MB default (configurable via `edutrack.storage.max-file-size`)
3. **School quota**: 1 GB default (configurable via `edutrack.storage.school-quota-bytes`)
4. **Late policy default**: ALLOW_FLAGGED — late submissions accepted but flagged
5. **Resubmission**: Replace — old text and attachments replaced; replaced files marked DELETED and removed by cleanup job
6. **Teacher visibility**: Only their own courses (where they are the assigned teacher for the stream-subject)
7. **Content validation**: Extension whitelist + magic number check (first bytes). Apache Tika dependency pending user approval.
8. **Cleanup job**: `@Scheduled` fixed-rate task, configurable interval (default 24h)
9. **Date-time**: API returns UTC instants; client formats using school timezone from School entity

## Proposed Changes

### Package Structure
```
com.peterscode.edutrack.lms
├── controller/
│   ├── LmsCourseController
│   ├── LmsTopicController
│   ├── LmsResourceController
│   ├── LmsAssignmentController
│   ├── LmsSubmissionController
│   ├── LmsMarkingController
│   ├── LmsAnnouncementController
│   ├── LmsFileController
│   ├── LmsStudentViewController
│   ├── LmsParentViewController
│   └── LmsModerationController
├── service/
│   ├── LmsAccessGuard          (central authorization)
│   ├── LmsCourseService
│   ├── LmsResourceService
│   ├── LmsAssignmentService
│   ├── LmsSubmissionService
│   ├── LmsMarkingService
│   ├── LmsAnnouncementService
│   ├── LmsModerationService
│   ├── FileStorageService      (interface)
│   ├── LocalFileStorageService (implementation)
│   └── LmsAuditService
├── repository/
│   ├── LmsCourseRepository
│   ├── LmsTopicRepository
│   ├── LmsResourceRepository
│   ├── LmsAssignmentRepository
│   ├── LmsSubmissionRepository
│   ├── LmsAnnouncementRepository
│   ├── StoredFileRepository
│   ├── LmsAttachmentRepository
│   ├── SchoolStorageUsageRepository
│   └── LmsAuditRepository
├── entity/
│   ├── LmsCourse
│   ├── LmsTopic
│   ├── LmsResource
│   ├── LmsAssignment
│   ├── LmsSubmission
│   ├── LmsAnnouncement
│   ├── StoredFile
│   ├── LmsAttachment
│   ├── SchoolStorageUsage
│   └── LmsAudit
├── dto/
│   ├── LmsRequests             (record-based, @JsonIgnoreProperties)
│   └── LmsViews                (view records)
├── exception/
│   └── LmsRuleException        (errorCode, HttpStatus, message)
├── event/
│   ├── LmsCoursePublishedEvent
│   ├── LmsAssignmentPublishedEvent
│   └── LmsSubmissionMarkedEvent
├── storage/
│   ├── FileScanner             (interface, no-op default)
│   └── LmsFileCleanupJob
└── audit/
    └── (LmsAudit entity + repository + service in main packages)
```

### Data Model

**V31 — Storage foundation:**
- `stored_file`: school_id (FK, NOT NULL), uploaded_by, original_name (sanitized, display only), content_type (validated), size_bytes, storage_key (UNIQUE, generated), sha256, scan_status (UNSCANNED/CLEAN/REJECTED), status (ACTIVE/DELETED), created_at
- `school_storage_usage`: school_id (UNIQUE, FK), used_bytes, version (@Version)
- `lms_audit`: school_id (FK, NOT NULL), entity_type, entity_id, action, actor_id, old_value (TEXT), new_value (TEXT), reason (VARCHAR 500), occurred_at (TIMESTAMP); indexes on (school_id, entity_type, entity_id), (school_id, occurred_at), (school_id, actor_id)

**V32 — Content:**
- `lms_course`: school_id (FK), stream_subject_id (FK), title, status (DRAFT/ACTIVE/ARCHIVED), created_by, version; UNIQUE(school_id, stream_subject_id)
- `lms_topic`: school_id (FK), course_id (FK), title, position; UNIQUE(course_id, position)
- `lms_resource`: school_id (FK), topic_id (FK), type (FILE/LINK/TEXT), title, body (TEXT), url, published, visible_from, created_by, version; CHECK: type='LINK' → url LIKE 'http%'
- `lms_attachment`: school_id (FK), owner_type (RESOURCE/ASSIGNMENT/SUBMISSION), owner_id, file_id (FK); UNIQUE(owner_type, owner_id, file_id)

**V33 — Assignments:**
- `lms_assignment`: school_id (FK), course_id (FK), title, instructions (TEXT), opens_at (TIMESTAMP), due_at (TIMESTAMP), max_score (DECIMAL 10,2), late_policy (BLOCK/ALLOW_FLAGGED), status (DRAFT/PUBLISHED/CLOSED), created_by, version
- `lms_submission`: school_id (FK), assignment_id (FK), student_id (FK), answer_text (TEXT), submitted_at, late, attempt_count, status (SUBMITTED/MARKED), score (DECIMAL 10,2), feedback (TEXT), released (BOOLEAN), marked_by, marked_at, version; UNIQUE(school_id, assignment_id, student_id)

**V34 — Announcements:**
- `lms_announcement`: school_id (FK), course_id (FK), title, body (TEXT), posted_by

All tables: school_id NOT NULL with FK to schools(id) and index. All entities extend BaseEntity with @SuperBuilder.

### Architecture Diagram

```mermaid
graph LR
    C1[LmsCourseController] --> S1[LmsCourseService]
    C2[LmsResourceController] --> S2[LmsResourceService]
    C3[LmsAssignmentController] --> S3[LmsAssignmentService]
    C4[LmsSubmissionController] --> S4[LmsSubmissionService]
    C5[LmsMarkingController] --> S5[LmsMarkingService]
    C6[LmsFileController] --> S6[FileStorageService]
    C7[LmsStudentViewController] --> S1
    C7 --> S3
    C7 --> S5
    C8[LmsParentViewController] --> S3
    C8 --> S5
    C9[LmsModerationController] --> S7[LmsModerationService]
    S1 --> G[LmsAccessGuard]
    S2 --> G
    S3 --> G
    S4 --> G
    S5 --> G
    S7 --> G
    S4 --> S6
    S2 --> S6
    S1 --> A[LmsAuditService]
    S2 --> A
    S3 --> A
    S4 --> A
    S5 --> A
    S7 --> A
    S6 --> FJ[LmsFileCleanupJob]
    G --> SS[StreamSubjectRepository]
    G --> SR[StudentRepository]
    G --> PR[ParentStudentMappingRepository]
    G --> GR[GuardianRepository]
    C1 --> MA[@RequiresModule LMS]
    C2 --> MA
    C3 --> MA
    C4 --> MA
    C5 --> MA
    C6 --> MA
    C7 --> MA
    C8 --> MA
    C9 --> MA
```

### Risks
1. **Testcontainers** adds Docker dependency for tests; CI must have Docker available
2. **File storage** is the highest-risk area; all validation must be thorough (magic numbers, path traversal, quota)
3. **Concurrent submission** handling requires careful use of unique constraints and retry logic
4. **No real virus scanner** integrated; FileScanner hook is no-op with UNSCANNED status
5. **Apache Tika** dependency (~2MB) needed for robust content validation — pending approval
6. **Spring Boot 4 / Spring Framework 7 / Jackson 3** are new; APIs verified against pom.xml versions

## Dependencies to Add
- `org.testcontainers:mysql` (test scope) — for integration tests with real MySQL and Flyway migrations
- `org.apache.tika:tika-core` (optional, for magic number validation) — pending user approval

# Testing

## Testing Strategy

### Test Infrastructure
- **Testcontainers on MySQL** with real Flyway migrations for integration tests
- **H2** for plain unit tests (mocking repositories)
- **@TempDir** for file storage in tests
- **Mock Clock** for time-sensitive tests

### Test Categories

#### Subscription Tests
- FREE, BASIC, STANDARD, PREMIUM schools get 403 MODULE_NOT_IN_PLAN on every LMS endpoint
- ENTERPRISE school works
- Upgrade and downgrade take effect on next request
- After grace period: reads work, writes return 402
- Tampered enabledModules row cannot exceed tier

#### Authorization Matrix Tests
- Unauthenticated → 401
- Wrong role → 403 (including LIBRARIAN)
- Another school's IDs → 404 with generic body
- Teacher assigned to different stream or different subject of same stream → 403 (cannot create, edit, or mark)
- Student A cannot read Student B's submission or files → 404
- Parent sees only linked child's released data → 404 for unlinked child
- Unreleased scores and feedback never appear for students or parents
- Other roles (including LIBRARIAN) → 403 on all LMS endpoints

#### File Storage Tests
- Allowed upload succeeds
- Oversize → 413
- Empty file rejected
- Disallowed type rejected (exe, sh, html, svg, zip)
- Double extension rejected
- Content/extension mismatch rejected
- Path traversal in filename rejected (../../x, null bytes)
- Stored key and path never from client input
- Downloading another school's, another stream's, or unpublished file → 404
- Response headers include attachment and nosniff
- Quota enforced under concurrent uploads (many threads, exactly permitted number succeed, usage matches reality)
- Orphan cleanup works

#### Concurrency Tests (real threads on MySQL)
- Double submit creates one row
- Marking vs release
- Two teachers editing same assignment → clean 409
- Two admins hiding same resource → one audit row

#### Behavior Tests
- Late flag and window with controllable Clock in school's timezone
- Resubmission replaces attachments
- Closed assignment rejects submissions
- Withdrawn or graduated students lose access immediately
- Paging limits and invalid sort → 400
- Query counts show no N+1 on list endpoints

#### Cross-Cutting Tests
- Unknown JSON properties → 400
- Error bodies contain nothing from another school
- Audit rows exist for every create, update, publish, mark, release, hide, and file action
- No personal data in audit values
- No JPA entities in responses

# Delivery Steps

### ✓ Step 1: Step 0: Investigation Report and Plan (No Code)
Deliver a detailed Step 0 investigation report with no production code. This stage produces:

**What exists (reused):**
- `ServiceModule.LMS` already in the enum (ENTERPRISE only) — no change needed
- `@RequiresModule` / `ModuleAccessAspect` (Order 201) — will annotate every LMS controller
- `ModuleAccessService` with grace period logic — reads work during grace, writes return 402
- `ParentAccessGuard` — static utility for parent-child verification (M6)
- `UserPrincipal`, `SecurityUtils`, `JwtAuthenticationFilter` — authentication layer
- `Clock` bean in `ApplicationConfig` — injectable for time-sensitive logic
- `GlobalExceptionHandler` — will add handler for `LmsRuleException`
- `ApiResponse` / `PageResponse` — response wrappers
- `BaseEntity` — extends for all LMS entities
- Library module conventions: `LibraryRuleException`, `LibraryRequests`/`LibraryViews` DTOs, `LibraryAudit`/`LibraryAuditService`, `@JsonIgnoreProperties(ignoreUnknown=false)` on DTOs
- `StreamSubject` entity with `teacherId`, `streamId`, `subjectId`, `academicYearId` — the course anchor
- `Student` entity with `streamId`, `status`, `active`, `admissionDate`
- `Staff` entity with `userId`, `status`, `active`
- `Role` enum: SUPER_ADMIN, SCHOOL_ADMIN, TEACHER, STUDENT, PARENT, LIBRARIAN
- `SchoolEntitlements.hasModule()` — enforces tier-based module access
- Event pattern: `TimetablePublishedEvent` (record) + `TimetablePublishedEventListener` with `@TransactionalEventListener`

**What is missing (to be built):**
- No file upload code exists anywhere
- No Testcontainers dependency — will add for integration tests
- No LMS entities, services, controllers, or migrations

**Data Model (10 tables, V31 migration):**

1. `lms_course` — school_id, stream_subject_id (FK, UNIQUE per school), title, status (DRAFT/ACTIVE/ARCHIVED), created_by, version
2. `lms_topic` — school_id, course_id (FK), title, position; UNIQUE(course_id, position)
3. `lms_resource` — school_id, topic_id (FK), type (FILE/LINK/TEXT), title, body, url, published, visible_from, created_by, version; CHECK: LINK type requires http/https url
4. `lms_assignment` — school_id, course_id (FK), title, instructions, opens_at, due_at, max_score, late_policy, status (DRAFT/PUBLISHED/CLOSED), created_by, version
5. `lms_submission` — school_id, assignment_id (FK), student_id (FK), answer_text, submitted_at, late, attempt_count, status (SUBMITTED/MARKED), score, feedback, released, marked_by, marked_at, version; UNIQUE(school_id, assignment_id, student_id)
6. `stored_file` — school_id, uploaded_by, original_name, content_type, size_bytes, storage_key (UNIQUE), sha256, scan_status (UNSCANNED/CLEAN/REJECTED), status (ACTIVE/DELETED), created_at
7. `lms_attachment` — school_id, owner_type (RESOURCE/ASSIGNMENT/SUBMISSION), owner_id, file_id (FK); UNIQUE(owner_type, owner_id, file_id)
8. `lms_announcement` — school_id, course_id (FK), title, body, posted_by
9. `school_storage_usage` — school_id (UNIQUE), used_bytes, version
10. `lms_audit` — school_id, entity_type, entity_id, action, actor_id, old_value, new_value, reason, occurred_at; indexes on (school_id, entity_type, entity_id), (school_id, occurred_at), (school_id, actor_id)

All tables: school_id NOT NULL with FK to schools(id) and index.

**Endpoints (following `/api/v1` prefix convention):**

```
Courses:
  POST   /api/v1/lms/courses              (TEACHER - create for their stream-subject)
  PUT    /api/v1/lms/courses/{id}         (TEACHER - update own)
  GET    /api/v1/lms/courses/me           (TEACHER - list my courses)
  GET    /api/v1/lms/courses              (SCHOOL_ADMIN - list all school courses)
  GET    /api/v1/lms/courses/{id}         (TEACHER/ADMIN - get one)

Topics:
  POST   /api/v1/lms/courses/{id}/topics  (TEACHER)
  PUT    /api/v1/lms/topics/{id}          (TEACHER)
  GET    /api/v1/lms/courses/{id}/topics  (TEACHER/STUDENT - if published)

Resources:
  POST   /api/v1/lms/topics/{id}/resources    (TEACHER)
  PUT    /api/v1/lms/resources/{id}           (TEACHER)
  POST   /api/v1/lms/resources/{id}/publish   (TEACHER)
  GET    /api/v1/lms/topics/{id}/resources    (TEACHER/STUDENT - if published)

Assignments:
  POST   /api/v1/lms/courses/{id}/assignments    (TEACHER)
  PUT    /api/v1/lms/assignments/{id}            (TEACHER)
  POST   /api/v1/lms/assignments/{id}/publish    (TEACHER)
  POST   /api/v1/lms/assignments/{id}/close      (TEACHER)
  GET    /api/v1/lms/courses/{id}/assignments    (TEACHER/STUDENT)

Submissions:
  POST   /api/v1/lms/assignments/{id}/submissions     (STUDENT - submit/resubmit)
  GET    /api/v1/lms/submissions/me                   (STUDENT - my submissions)
  GET    /api/v1/lms/assignments/{id}/submissions     (TEACHER - course submissions)
  GET    /api/v1/lms/submissions/{id}                 (STUDENT own / TEACHER / ADMIN)

Marking:
  PUT    /api/v1/lms/submissions/{id}/mark            (TEACHER)
  POST   /api/v1/lms/assignments/{id}/release-all     (TEACHER)
  POST   /api/v1/lms/submissions/{id}/release         (TEACHER)

Announcements:
  POST   /api/v1/lms/courses/{id}/announcements       (TEACHER)
  GET    /api/v1/lms/courses/{id}/announcements       (TEACHER/STUDENT)

Files:
  POST   /api/v1/lms/files/upload                     (multipart, authenticated)
  GET    /api/v1/lms/files/{fileId}/download          (authorized download)
  HEAD   /api/v1/lms/files/{fileId}/download          (metadata only)

Student Views:
  GET    /api/v1/lms/student/courses                  (STUDENT - my courses)
  GET    /api/v1/lms/student/assignments              (STUDENT - my assignments with state)
  GET    /api/v1/lms/student/scores                   (STUDENT - released scores)

Parent Views:
  GET    /api/v1/lms/parent/children/{id}/courses     (PARENT - linked child)
  GET    /api/v1/lms/parent/children/{id}/assignments (PARENT)
  GET    /api/v1/lms/parent/children/{id}/scores      (PARENT - released only)

Admin Moderation:
  POST   /api/v1/lms/moderate/hide                   (SCHOOL_ADMIN - hide content with reason)
  POST   /api/v1/lms/moderate/remove                 (SCHOOL_ADMIN - remove content with reason)

Storage:
  GET    /api/v1/lms/storage/usage                   (SCHOOL_ADMIN - school storage usage)
```

**Dependencies to add:**
- `org.testcontainers:mysql` (test scope) — for integration tests with real MySQL and Flyway migrations
- `org.apache.tika:tika-core` (optional, for magic number validation) — **awaiting user approval**

**Decisions needing your approval:**
1. **Apache Tika dependency** — should I add `tika-core` (~2MB) for content-type validation by magic numbers? Alternative: validate by extension only (less secure) or implement minimal magic number checks manually.
2. **File content validation** — without Tika, I can check the first bytes for common types (PDF %PDF, PNG \x89PNG, JPEG FF D8 FF, ZIP/DOCX PK). Is this sufficient?
3. **Cleanup job scheduling** — should the cleanup job run as a `@Scheduled` fixed-rate task (configurable interval, default 24h) or as a manual endpoint triggered by admin?
4. **Pagination default** — confirm default page size 20, max 100 (already set in `application.yaml` `spring.data.web.pageable.max-page-size: 100`).
5. **School timezone for display** — the school's timezone is stored in `School.timezone`. Should all date-time display use the school's timezone, or should the API return UTC instants and let the client format?

**Risks:**
- Testcontainers adds Docker dependency for tests; CI must have Docker available
- File storage is the highest-risk area; all validation must be thorough
- Concurrent submission handling requires careful use of unique constraints and retry logic
- No real virus scanner integrated; the `FileScanner` hook will be no-op with `UNSCANNED` status

### ✓ Step 2: Phase 1: Storage Foundation (Entities, FileStorageService, Validation, Quota, Audit, Cleanup)
Build the storage foundation with tests first:

**Entities:**
- `StoredFile` — extends BaseEntity, @SuperBuilder, school_id NOT NULL FK, storage_key UNIQUE, sha256, scan_status, status (ACTIVE/DELETED), @Version
- `SchoolStorageUsage` — school_id UNIQUE FK, used_bytes BIGINT, @Version for row locking
- `LmsAudit` — extends BaseEntity pattern from LibraryAudit, with indexes

**Services:**
- `FileStorageService` interface with `upload()`, `download()`, `delete()`, `head()` methods
- `LocalFileStorageService` implementation:
  - Configured by `edutrack.storage.base-path` (mandatory, validated at startup)
  - Validation: absolute path, no '..', outside project dir and static resources, exists or creatable, writable, not world-readable
  - Configurable `edutrack.storage.max-file-size` (default 10MB)
  - Configurable `edutrack.storage.school-quota-bytes` (default 1GB)
  - Configurable `edutrack.storage.allowed-extensions` (default: pdf, doc, docx, ppt, pptx, xls, xlsx, txt, png, jpg, jpeg)
  - Configurable `edutrack.storage.min-free-bytes` (minimum free disk space before accepting uploads)
  - Storage key: UUID-based, path is `{schoolId}/{storageKey}` (unguessable)
  - Original filename: sanitized (reject path separators, null bytes, traversal; bounded length 255)
  - Content validation: extension whitelist + magic number check (first bytes)
  - Reject: empty files, executables, scripts, html, svg, archives, double extensions, extension/content mismatch
  - SHA-256 checksum computed and stored
  - Quota enforcement: lock `SchoolStorageUsage` row, check used_bytes + new_size <= quota, compensate on failure
- `FileScanner` interface with no-op default (scanStatus stays UNSCANNED)
- `LmsAuditService` — append-only audit entries, written in the same transaction as the action

**Exception:**
- `LmsRuleException` — errorCode, HttpStatus, message (following LibraryRuleException pattern)

**Controller:**
- `LmsFileController` with `@RequiresModule(ServiceModule.LMS)`:
  - `POST /api/v1/lms/files/upload` — multipart upload with validation
  - `GET /api/v1/lms/files/{fileId}/download` — authorized download with headers (Content-Disposition: attachment, X-Content-Type-Options: nosniff)
  - `HEAD /api/v1/lms/files/{fileId}/download` — metadata without body

**Cleanup Job:**
- `LmsFileCleanupJob` — `@Scheduled` fixed-rate (configurable, default 24h), removes DELETED files and orphans after configurable grace period, idempotent, per-school with limits

**Migration:** V31__create_lms_storage.sql (stored_file, school_storage_usage, lms_audit tables)

**Tests (Testcontainers on MySQL with real Flyway migrations):**
- Upload succeeds for allowed types
- Oversize returns 413
- Empty file rejected
- Disallowed type rejected (exe, sh, html, svg, zip)
- Double extension rejected
- Content/extension mismatch rejected
- Path traversal in filename rejected (../../x, null bytes)
- Stored key never from client input
- Quota enforced under concurrent uploads (many threads, exactly permitted number succeed)
- Orphan cleanup works
- Response headers include attachment and nosniff
- Unknown JSON properties return 400
- Wrong role returns 403
- Other school returns 404

### ✓ Step 3: Phase 2: Courses, Topics, and Resources
Implement courses, topics, and resources with full authorization:

**Entities:**
- `LmsCourse` — school_id, stream_subject_id (UNIQUE per school), title, status (DRAFT/ACTIVE/ARCHIVED), created_by, @Version
- `LmsTopic` — school_id, course_id (FK), title, position; UNIQUE(course_id, position)
- `LmsResource` — school_id, topic_id (FK), type (FILE/LINK/TEXT), title, body, url, published, visible_from, created_by, @Version; CHECK: LINK requires http/https url
- `LmsAttachment` — school_id, owner_type (RESOURCE/ASSIGNMENT/SUBMISSION), owner_id, file_id; UNIQUE(owner_type, owner_id, file_id)

**Services:**
- `LmsAccessGuard` — central authorization: `assertCourseTeacher()`, `assertStudentInCourse()`, `assertParentOfStudent()`, `assertAdmin()`
- `LmsCourseService` — create (teacher assigned to stream-subject), update, archive, list mine, list for admin
- `LmsResourceService` — create, update, publish, hide; link files via LmsAttachment

**DTOs:**
- `LmsRequests` — record-based DTOs with @Valid, @JsonIgnoreProperties(ignoreUnknown=false)
- `LmsViews` — view records (no JPA entities in responses)

**Controllers:**
- `LmsCourseController` with `@RequiresModule(ServiceModule.LMS)` and `@PreAuthorize` per endpoint
- `LmsTopicController`
- `LmsResourceController`

**Events:**
- `LmsCoursePublishedEvent` — published after commit

**Migration:** V32__create_lms_content.sql (lms_course, lms_topic, lms_resource, lms_attachment)

**Tests:**
- Teacher assigned to stream-subject creates course; teacher assigned to different stream or subject gets 403
- Student sees only ACTIVE course content from their stream
- Student from different stream gets 404
- SCHOOL_ADMIN can view all courses
- LIBRARIAN gets 403 on all LMS endpoints
- Unauthenticated gets 401
- Cross-tenant IDs return 404
- Pagination works (default 20, max 100)
- Invalid sort returns 400
- Unknown JSON properties return 400
- Audit rows exist for create, update, publish
- Link resources validate http/https only
- No N+1 queries on list endpoints

### ✓ Step 4: Phase 3: Assignments and Submissions with Concurrency
Implement assignments and submissions with concurrency rules:

**Entities:**
- `LmsAssignment` — school_id, course_id (FK), title, instructions, opens_at, due_at (INSTANT), max_score (DECIMAL), late_policy (BLOCK/ALLOW_FLAGGED, default ALLOW_FLAGGED), status (DRAFT/PUBLISHED/CLOSED), created_by, @Version
- `LmsSubmission` — school_id, assignment_id (FK), student_id (FK), answer_text, submitted_at, late, attempt_count, status (SUBMITTED/MARKED), score, feedback, released, marked_by, marked_at, @Version; UNIQUE(school_id, assignment_id, student_id)

**Services:**
- `LmsAssignmentService` — create, update, publish, close, list
- `LmsSubmissionService` — submit, resubmit, list for course, view one
  - Submission window: not before opens_at; after due_at rejected (BLOCK) or accepted and flagged (ALLOW_FLAGGED)
  - CLOSED or unpublished assignment rejects submissions
  - Resubmission replaces text and attachments; replaced files marked DELETED
  - Concurrent double-submit: unique constraint + clean 409 on conflict (retry-safe idempotency)
  - Uses injected Clock for time checks

**Controllers:**
- `LmsAssignmentController`
- `LmsSubmissionController`

**Events:**
- `LmsAssignmentPublishedEvent`

**Migration:** V33__create_lms_assignments.sql (lms_assignment, lms_submission)

**Tests:**
- Submission before opens_at rejected
- Submission after due_at: BLOCK rejects, ALLOW_FLAGGED accepts with late=true
- CLOSED assignment rejects submissions
- Resubmission replaces attachments (old files marked DELETED)
- Double submit creates exactly one row (concurrent threads on MySQL)
- Student A cannot read Student B's submission (404)
- Withdrawn/graduated student (status != ACTIVE) loses access immediately
- Late flag with controllable Clock in school's timezone
- Two teachers editing same assignment get clean 409 (optimistic lock)
- Audit rows for create, publish, submit, resubmit

### ✓ Step 5: Phase 4: Marking, Release, Announcements, Student and Parent Views
Implement marking, release, announcements, student views, and parent views:

**Services:**
- `LmsMarkingService` — mark submission (score 0 to maxScore, BigDecimal scale 2), release one, bulk release
  - Score validation: between 0 and maxScore
  - Feedback length-limited
  - Students see score/feedback only after released=true
  - Change after release requires reason, audited
  - Stale update returns 409 VERSION_CONFLICT
- `LmsAnnouncementService` — create, list

**Controllers:**
- `LmsMarkingController`
- `LmsAnnouncementController`
- `LmsStudentViewController` — my courses, my assignments with state, my released scores
- `LmsParentViewController` — linked child's courses, assignments, released scores (using ParentAccessGuard)

**Events:**
- `LmsSubmissionMarkedEvent` — published after commit

**Migration:** V34__create_lms_announcements.sql (lms_announcement)

**Tests:**
- Score between 0 and maxScore enforced
- Student sees score/feedback only after released=true
- Unreleased scores never appear for students or parents
- Parent sees only linked child's released data (404 for unlinked child)
- Parent with no linked child gets 404
- Releasing by course teacher only
- Bulk release works
- Change after release audited with reason
- Stale marking update returns 409
- Student views: my courses (only their stream, ACTIVE, enrolled), my assignments with state, released scores
- Announcement create and list
- Other roles (LIBRARIAN) get 403 on all endpoints
- Authorization matrix: unauthenticated 401, wrong role 403, other school 404
- Subscription: FREE/BASIC/STANDARD/PREMIUM get 403 MODULE_NOT_IN_PLAN; ENTERPRISE works
- After grace period: reads work, writes return 402
- Unknown JSON properties return 400 on every DTO
- Audit rows for mark, release, announcement

### ✓ Step 6: Phase 5: Moderation, Reports, Security Review, and Final Validation
Complete the module with admin moderation, storage usage endpoint, security review, and final validation:

**Services:**
- `LmsModerationService` — hide or remove content with mandatory reason, audited
- Storage usage endpoint in existing controller

**Controllers:**
- `LmsModerationController` — SCHOOL_ADMIN only
- Add `GET /api/v1/lms/storage/usage` to storage controller

**GlobalExceptionHandler:**
- Add handler for `LmsRuleException` (following LibraryRuleException pattern)

**Configuration:**
- Add `edutrack.storage.*` properties with validation at startup
- Add multipart size limits consistent with file size limits
- Add `@Bean` for `FileScanner` (no-op default)

**Migration:** No new migration needed (all tables created in V31-V34)

**Security Review:**
- Verify every endpoint has @RequiresModule(ServiceModule.LMS)
- Verify every endpoint has @PreAuthorize with correct roles
- Verify cross-tenant access returns 404 (not 403)
- Verify LIBRARIAN and other roles get 403 on all LMS endpoints
- Verify unauthenticated gets 401
- Verify file download authorization (M1-M3, M6)
- Verify no personal data in audit values
- Verify no JPA entities in responses
- Verify unknown JSON properties rejected

**Tests:**
- Full authorization matrix over every endpoint
- Admin hide/remove with reason audited
- Two admins hiding same resource produce one audit row
- Storage usage endpoint returns correct data
- Concurrency: marking vs release
- Query counts show no N+1 on list endpoints
- Paging limits and invalid sort return 400
- Cross-cutting: unknown JSON properties give 400
- Error bodies contain nothing from another school
- All rules M1-M11 and S1-S8 verified with named tests

**Final Summary:**
1. Done, with proof (test names)
2. Not done or deferred (state plainly: no virus scanner integrated)
3. Rules table: M1-M11, S1-S8 with enforcement location and test name
4. Security findings fixed and open
5. Migrations: V31-V34 files
6. API changes: all endpoints listed
7. Decisions log
8. Manual verification curl steps
9. Git: branch, commits, push result, PR text
10. Final `./mvnw clean verify` result with test totals