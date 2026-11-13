---
sessionId: session-261101-120040-13tu
---

# Requirements

Implement the Library module for EduTrack. Add LIBRARIAN role, catalogue (categories, books, copies), borrowing (issue, return, renew, lost), fines, borrower lookup, views, clearance, reports. LIBRARIAN always belongs to one school. SCHOOL_ADMIN creates LIBRARIAN in own school; SUPER_ADMIN with school_id. MODULE_NOT_IN_PLAN if LIBRARY not enabled. LIBRARIAN must NOT reach student/staff management, grading, attendance, timetable, examination, fees. Catalogue search with pagination, filters, whitelist sort, wildcard escaping. Borrowing with fixed lock ordering, @Version, Clock-based dates. Fines: SCHOOL_ADMIN only for assess; LIBRARIAN 403. Views: borrower lookup minimal profile, student/teacher own loans, parent linked child via ParentAccessGuard. Dedicated library_audit table. Testcontainers on MySQL for library tests.

# Technical Design

Role stored as VARCHAR(50) - no migration needed for enum value. Latest migration V27. Add LIBRARIAN to Role enum, update creatableBy() and requiresSameSchool(). Add createLibrarian to UserRegistrationService with module gate. Add endpoint to UserManagementController. LibraryRuleException with handler in GlobalExceptionHandler. Entities: LibraryCategory, Book, BookCopy, BorrowingPolicy, LibraryBorrowerAccount, Loan, LibraryFine, LibraryAudit. Migrations: V28 (LIBRARIAN doc), V29 (catalogue), V30 (borrowing + audit). Flat library package. Testcontainers for library only. BorrowerAccount upsert. Loan UNIQUE(copy_id, active_marker). Overdue derived at read time. Default policy from config. LIBRARIAN denied from non-library endpoints via service checks.

# Testing

Testcontainers on MySQL with Flyway for integration tests. Mockito for unit tests. Role: create, login, principal, cross-tenant 404. Security: LIBRARIAN on non-library endpoints 403. Subscription: FREE/BASIC/STANDARD 403, PREMIUM works. Catalogue: CRUD, search, no N+1. Borrowing: issue, return, renew, lost. Concurrency: 20 simultaneous issues 1 success. Fines: overdue PENDING_ASSESSMENT, assess SCHOOL_ADMIN only, LIBRARIAN 403. Views: student own loans, parent linked child, lookup minimal. Cross-cutting: cross-tenant 404, unknown JSON 400, @Version 409.

# Delivery Steps

### ✓ Step 1: Phase 1: LIBRARIAN Role and Security
Add LIBRARIAN role and audit security.
- Add LIBRARIAN to Role enum; update creatableBy() and requiresSameSchool()
- Add createLibrarian to UserRegistrationService with module gate
- Add POST /api/v1/admin/librarians endpoint
- V28 migration (documentation)
- LibraryRuleException and GlobalExceptionHandler handler
- Fix isAuthenticated() endpoints for LIBRARIAN denial
- Tests: role, login, principal, cross-tenant, security, subscription

### ✓ Step 2: Phase 2: Catalogue and Search
Implement catalogue entities and search.
- V29 migration: library_categories, books, book_copies
- Entities: LibraryCategory, Book, BookCopy
- Repositories with school-scoped queries
- LibraryCatalogueService and Controller
- Search with whitelist sort and wildcard escaping
- Tests: CRUD, search, pagination, role access

### ✓ Step 3: Phase 3: Borrowing and Concurrency
Implement borrowing with concurrency safety.
- V30 migration: borrowing_policies, borrower_accounts, loans, library_audit
- Entities: BorrowingPolicy, LibraryBorrowerAccount, Loan, LibraryAudit
- LibraryBorrowingService with fixed lock ordering and retry
- LibraryAuditService append-only
- Tests: issue, return, renew, lost, concurrency

### ✓ Step 4: Phase 4: Fines
Implement fines management.
- LibraryFine entity with @Version
- LibraryFineService: assess (SCHOOL_ADMIN only), waive, pay
- LIBRARIAN 403 on assess/waive/pay
- Tests: overdue, assess, waive, pay, LIBRARIAN 403, concurrent

### ✓ Step 5: Phase 5: Views and Final Review
Implement views, lookup, reports, finalize.
- LibraryViewsService: lookup, self-service, parent, clearance, reports
- Add Testcontainers dependency and config
- Full security review
- Tests: lookup, self-service, parent, cross-tenant, @Version
- Git branch, commits, push, PR