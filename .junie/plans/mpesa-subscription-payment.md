---
sessionId: session-261101-134537-1wxr
---

# Technical Design

### Overview & Goals
Build a subscription payment system that allows schools to pay for their selected subscription tier using Mpesa (Lipa na Mpesa via business till number). When a school pays, the system automatically detects the payment via Mpesa C2B webhooks and instantly activates/extends the subscription — no admin intervention required.

### User Stories
1. **As a school admin**, I want to browse available subscription tiers and their prices (per term and per academic year) so I can choose the right plan.
2. **As a school admin**, I want to initiate a payment for a selected tier so I can activate or upgrade my subscription.
3. **As a school admin**, I want to pay via Mpesa using a unique invoice reference so the system knows which school paid.
4. **As a school admin**, I want to receive an email receipt after successful payment for my records.
5. **As a super admin**, I want to configure subscription prices per tier and billing period via the API so I can manage pricing without code changes.
6. **As a super admin**, I want to view all payment transactions and their status for audit and dispute resolution.

### Scope

**In Scope:**
- Subscription price catalog management (admin API)
- Invoice generation when a school initiates payment
- Mpesa C2B integration (validation + confirmation callbacks)
- Auto-activation of subscription on confirmed payment
- Invoice expiry (24 hours)
- Payment transaction audit trail
- Email receipts on successful payment
- School payment history view

**Out of Scope:**
- Bank transfer payments
- Card payments (Stripe)
- Proration of existing subscriptions
- Refund processing
- Mpesa STK push (only till number / C2B)

### Functional Requirements

**Price Management:**
- Admin can create, update, and view prices per `SubscriptionTier` × `BillingPeriod` (TERM, ACADEMIC_YEAR)
- Prices stored in database with currency support

**Invoice Generation:**
- School admin selects a tier and billing period
- System generates a unique invoice with: invoice number, amount, tier, period, expiry (24h), and Mpesa payment instructions (till number, account number = invoice reference)
- Invoice status: PENDING → VALIDATED → CONFIRMED / EXPIRED / FAILED

**Mpesa C2B Flow:**
1. School pays to the EduTrack till number, entering the invoice reference as the Account field
2. Safaricom hits `/api/v1/payments/mpesa/callback/validation` — system validates the invoice exists, amount matches, and accepts
3. Safaricom hits `/api/v1/payments/mpesa/callback/confirmation` — system confirms payment, activates subscription, sends email receipt

**Auto-Activation:**
- On confirmed payment, the system:
  - Updates the school's `subscriptionTier` to the requested tier
  - Sets `subscriptionStartDate` = day after current subscription ends (or today if no active subscription)
  - Sets `subscriptionEndDate` based on billing period (term dates or academic year)
  - Updates `subscriptionStatus` to ACTIVE
  - Enables the modules for the new tier
  - Updates capacity limits (max students/teachers)
  - Records the transaction in the audit log
  - Sends email receipt to school admin

**Invoice Expiry:**
- Pending invoices auto-expire after 24 hours
- Schools can re-initiate payment if expired

### Non-Functional Requirements
- **Security:** Webhook endpoints must validate Mpesa signatures and protect against replay attacks
- **Reliability:** Mpesa retries callbacks up to 3 times; system must be idempotent
- **Audit:** All payment events logged with timestamps, amounts, Mpesa transaction IDs
- **Idempotency:** Processing the same confirmation twice must not double-activate the subscription

# Technical Design

### Current Implementation
- **Subscription management** is handled by `SchoolService` / `SchoolServiceImpl` with `UpdateSchoolSubscriptionRequestDto`. Only SUPER_ADMIN can update subscriptions via `PUT /api/v1/schools/{id}/subscription`.
- **Tier definitions** are in `SubscriptionTier` enum (FREE, BASIC, STANDARD, PREMIUM, ENTERPRISE).
- **Plan limits** are defined in `PlanCatalog` (max students/teachers per tier).
- **Subscription terms** are resolved by `SubscriptionTermsResolver` (start/end date validation).
- **Module access** is enforced by `ModuleAccessService` and `PlanGuard`.
- **School entity** (`School.java`) stores `subscriptionTier`, `subscriptionStatus`, `subscriptionStartDate`, `subscriptionEndDate`, `maxStudents`, `maxTeachers`, `enabledModules`.
- **No payment infrastructure exists** — no payment entities, no billing module, no Mpesa integration, no webhooks.
- The project uses **Spring Boot 4.1.1**, **JPA with MySQL**, **Flyway** for migrations, **Lombok**, and **Spring Mail** for email.

### Key Decisions
1. **New `payment` module** — All payment-related code will live in `com.peterscode.edutrack.payment/` to keep it isolated from the school module.
2. **Mpesa C2B with validation + confirmation** — Two callback endpoints ensure reliability. Validation pre-associates the payment, confirmation finalizes it.
3. **Invoice-based tracking** — Each payment initiation creates an invoice with a unique reference number. Schools enter this as the Mpesa Account field. This is the primary key for matching webhooks to schools.
4. **Database-driven pricing** — `SubscriptionPrice` entity stores prices per tier × billing period. Admin manages via REST API.
5. **Idempotent webhook processing** — Each callback checks invoice status to prevent double-processing.
6. **New period after current ends** — No proration. If a school has an active subscription, the new period starts the day after the current `subscriptionEndDate`.
7. **24-hour invoice expiry** — A scheduled job or lazy check on invoice retrieval expires old invoices.

### Proposed Changes

**New Entities:**
- `SubscriptionPrice` — Stores price per `SubscriptionTier` × `BillingPeriod` (TERM, ACADEMIC_YEAR). Fields: id, tier, billingPeriod, amount, currency, active, effectiveFrom.
- `PaymentInvoice` — Represents a payment request. Fields: id, invoiceNumber (unique), school (FK), tier, billingPeriod, amount, currency, status (PENDING, VALIDATED, CONFIRMED, EXPIRED, FAILED), mpesaTransactionId, validatedAt, confirmedAt, expiresAt, createdAt.
- `PaymentTransaction` — Audit trail of all payment events. Fields: id, invoice (FK), eventType (INVOICE_CREATED, VALIDATION_RECEIVED, VALIDATION_ACCEPTED, CONFIRMATION_RECEIVED, SUBSCRIPTION_ACTIVATED, INVOICE_EXPIRED, FAILED), mpesaTransactionId, amount, details (JSON), createdAt.

**New Enums:**
- `BillingPeriod` — TERM, ACADEMIC_YEAR
- `PaymentInvoiceStatus` — PENDING, VALIDATED, CONFIRMED, EXPIRED, FAILED
- `PaymentTransactionEventType` — INVOICE_CREATED, VALIDATION_RECEIVED, VALIDATION_ACCEPTED, CONFIRMATION_RECEIVED, SUBSCRIPTION_ACTIVATED, INVOICE_EXPIRED, FAILED

**New Services:**
- `SubscriptionPriceService` — CRUD for subscription prices (admin only).
- `PaymentInvoiceService` — Create invoices, match webhooks to invoices, handle expiry.
- `MpesaCallbackService` — Process validation and confirmation callbacks from Safaricom.
- `PaymentNotificationService` — Send email receipts on confirmed payments.

**New Controllers:**
- `SubscriptionPriceController` — Admin API for managing prices (`/api/v1/subscription-prices`).
- `PaymentController` — School payment initiation and history (`/api/v1/payments`).
- `MpesaWebhookController` — Public webhook endpoints for Mpesa callbacks (`/api/v1/payments/mpesa/callback`).

**Modified Services:**
- `SchoolServiceImpl.updateSchoolSubscription()` — No changes needed; the payment system will call the same internal logic to activate subscriptions.
- A new internal method in `SchoolService` for payment-triggered activation (to avoid exposing the admin endpoint to the payment flow).

### Data Models / Contracts

**SubscriptionPrice Entity:**
```
id (Long), tier (SubscriptionTier), billingPeriod (BillingPeriod),
amount (BigDecimal), currency (String), active (boolean),
effectiveFrom (LocalDate), createdAt, updatedAt
```

**PaymentInvoice Entity:**
```
id (Long), invoiceNumber (String, unique), school (FK → School),
tier (SubscriptionTier), billingPeriod (BillingPeriod),
amount (BigDecimal), currency (String),
status (PaymentInvoiceStatus), mpesaTransactionId (String),
validatedAt (LocalDateTime), confirmedAt (LocalDateTime),
expiresAt (LocalDateTime), createdAt, updatedAt
```

**PaymentTransaction Entity:**
```
id (Long), invoice (FK → PaymentInvoice),
eventType (PaymentTransactionEventType),
mpesaTransactionId (String), amount (BigDecimal),
details (String/JSON), createdAt
```

**API Endpoints:**

```
GET  /api/v1/subscription-prices              — List active prices (public/school)
POST /api/v1/subscription-prices              — Create price (SUPER_ADMIN)
PUT  /api/v1/subscription-prices/{id}         — Update price (SUPER_ADMIN)
POST /api/v1/payments/invoice                 — Create invoice for current school (SCHOOL_ADMIN)
GET  /api/v1/payments/invoice/{invoiceNumber} — Get invoice details
GET  /api/v1/payments/history                 — School payment history (SCHOOL_ADMIN)
POST /api/v1/payments/mpesa/callback/validation  — Mpesa validation (public)
POST /api/v1/payments/mpesa/callback/confirmation — Mpesa confirmation (public)
GET  /api/v1/payments/admin/transactions      — All transactions (SUPER_ADMIN)
```

**Mpesa Validation Callback Request:**
```
{ "Body": { "stkCallback": { "CallbackMetadata": { ... }, 
  "CheckoutRequestID": "...", "ResultCode": 0, 
  "ResultDesc": "..." } } }
```

**Mpesa Confirmation Callback Request:**
```
{ "Body": { "stkCallback": { "CallbackMetadata": { 
  items: [{ "Name": "Amount", "Value": 1000 },
           { "Name": "MpesaReceiptNumber", "Value": "..." },
           { "Name": "TransactionDate", "Value": 20250101 },
           { "Name": "PhoneNumber", "Value": "254712345678" }
         ]
  }, "CheckoutRequestID": "...", "ResultCode": 0, 
  "ResultDesc": "..." } } }
```

### File Structure

```
src/main/java/com/peterscode/edutrack/payment/
├── entity/
│   ├── SubscriptionPrice.java
│   ├── PaymentInvoice.java
│   └── PaymentTransaction.java
├── enums/
│   ├── BillingPeriod.java
│   ├── PaymentInvoiceStatus.java
│   └── PaymentTransactionEventType.java
├── dto/
│   ├── SubscriptionPriceDto.java
│   ├── CreateSubscriptionPriceRequestDto.java
│   ├── PaymentInvoiceDto.java
│   ├── CreatePaymentInvoiceRequestDto.java
│   ├── MpesaCallbackRequest.java
│   └── PaymentTransactionDto.java
├── repository/
│   ├── SubscriptionPriceRepository.java
│   ├── PaymentInvoiceRepository.java
│   └── PaymentTransactionRepository.java
├── service/
│   ├── SubscriptionPriceService.java
│   ├── SubscriptionPriceServiceImpl.java
│   ├── PaymentInvoiceService.java
│   ├── PaymentInvoiceServiceImpl.java
│   ├── MpesaCallbackService.java
│   ├── MpesaCallbackServiceImpl.java
│   └── PaymentNotificationService.java
├── controller/
│   ├── SubscriptionPriceController.java
│   ├── PaymentController.java
│   └── MpesaWebhookController.java
└── config/
    └── MpesaConfig.java

src/main/resources/db/migration/
├── V__create_subscription_prices.sql
├── V__create_payment_invoices.sql
└── V__create_payment_transactions.sql
```

### Architecture Diagram

``` mermaid
graph LR
    A[School Admin] -->|1. Select tier| B[PaymentController]
    B -->|2. Create invoice| C[PaymentInvoiceService]
    C -->|3. Save invoice| D[(Payment DB)]
    C -->|4. Return invoice details| A
    A -->|5. Pay via Mpesa till| E[Safaricom C2B]
    E -->|6. Validation callback| F[MpesaWebhookController]
    F -->|7. Validate invoice| C
    C -->|8. Accept validation| E
    E -->|9. Confirmation callback| F
    F -->|10. Confirm payment| C
    C -->|11. Update subscription| G[SchoolService]
    G -->|12. Update school| D
    C -->|13. Log transaction| D
    C -->|14. Send email| H[PaymentNotificationService]
    H -->|15. Email receipt| A
```

### Risks
- **Mpesa sandbox vs production:** The C2B API behavior differs between sandbox and production. Thorough testing in sandbox required before going live.
- **Webhook delivery failures:** Safaricom retries up to 3 times. If all fail, a background job should check for VALIDATED invoices that never received confirmation and retry or alert admin.
- **Invoice reference confusion:** Schools must enter the exact invoice number as the Mpesa Account field. Clear UI instructions are critical.
- **Currency mismatch:** Prices are stored with currency; Mpesa KES is default. Multi-currency support may be needed later.
- **Concurrent payments:** A school might initiate multiple invoices. The system should prevent duplicate active invoices for the same tier.

# Delivery Steps

### ✓ Step 1: Create payment entities, enums, and repositories
Set up the data layer for the payment module with all entities, enums, repositories, and Flyway migrations.
- Create `BillingPeriod`, `PaymentInvoiceStatus`, and `PaymentTransactionEventType` enums in `com.peterscode.edutrack.payment.enums/`.
- Create `SubscriptionPrice`, `PaymentInvoice`, and `PaymentTransaction` JPA entities extending `BaseEntity` with proper relationships (PaymentInvoice → School FK, PaymentTransaction → PaymentInvoice FK).
- Create Spring Data JPA repositories: `SubscriptionPriceRepository`, `PaymentInvoiceRepository` (with `findByInvoiceNumber`, `findByStatusAndExpiresBefore`), and `PaymentTransactionRepository`.
- Create Flyway migration scripts to create the three tables with proper indexes (unique index on invoice_number, composite index on tier+billingPeriod for prices).
- Follow the project's existing entity patterns (Lombok `@Entity`, `@Getter`, `@Setter`, `@SuperBuilder`, `@Table`).

### ✓ Step 2: Implement subscription price management
Build the admin-facing price catalog so subscription prices can be configured via API.
- Create `SubscriptionPriceService` interface and `SubscriptionPriceServiceImpl` with methods: `getActivePrices()`, `getPrice(tier, billingPeriod)`, `createPrice()`, `updatePrice()`, `deactivatePrice()`.
- Create DTOs: `SubscriptionPriceDto`, `CreateSubscriptionPriceRequestDto` (with `@NotNull` on tier, billingPeriod, amount; `@Positive` on amount).
- Create `SubscriptionPriceController` with endpoints: `GET /api/v1/subscription-prices` (public), `POST` (SUPER_ADMIN), `PUT /{id}` (SUPER_ADMIN).
- Create `MpesaConfig` to hold Mpesa configuration properties (till number, short code, passkey, environment) from `application.yml`.
- Prices are stored with currency (default KES) and an `active` flag for soft deactivation.

### ✓ Step 3: Implement invoice generation and payment initiation
Enable school admins to initiate a subscription payment by generating an invoice.
- Create `PaymentInvoiceService` interface and `PaymentInvoiceServiceImpl`.
- Implement `createInvoice(schoolId, tier, billingPeriod)`: look up the active price, generate a unique invoice number (e.g., `EDU-{schoolCode}-{timestamp}`), set expiry to 24 hours, save as PENDING, create a PaymentTransaction log entry.
- Implement `getInvoice(invoiceNumber)` and `getSchoolInvoices(schoolId)`.
- Implement `expireInvoices()` — a method that sets expired invoices to EXPIRED status (called lazily on invoice retrieval and by a scheduled job).
- Create `PaymentController` with endpoints: `POST /api/v1/payments/invoice` (SCHOOL_ADMIN, creates invoice for current school), `GET /api/v1/payments/invoice/{invoiceNumber}`, `GET /api/v1/payments/history`.
- Create DTOs: `PaymentInvoiceDto`, `CreatePaymentInvoiceRequestDto` (tier, billingPeriod), `PaymentTransactionDto`.
- Add `@Scheduled(fixedRate = 3600000)` to check and expire old invoices every hour.

### ✓ Step 4: Implement Mpesa C2B webhook callbacks
Build the Mpesa webhook endpoints that receive validation and confirmation callbacks from Safaricom.
- Create `MpesaWebhookController` with two public (permitAll) endpoints:
  - `POST /api/v1/payments/mpesa/callback/validation` — receives validation request, looks up invoice by Account number, validates amount matches, returns ACCEPT response to Safaricom.
  - `POST /api/v1/payments/mpesa/callback/confirmation` — receives confirmation, looks up invoice, updates status to CONFIRMED, triggers subscription activation.
- Create `MpesaCallbackService` and `MpesaCallbackServiceImpl`:
  - `handleValidation(callback)`: find invoice by the Account field (invoice number), verify amount matches, update status to VALIDATED, log transaction.
  - `handleConfirmation(callback)`: find VALIDATED/CONFIRMED invoice, if already CONFIRMED skip (idempotency), update status to CONFIRMED, store Mpesa transaction ID, call `activateSubscription()`.
- Create `MpesaCallbackRequest` DTOs for parsing Safaricom's JSON callback format (Body → stkCallback → CallbackMetadata items).
- Ensure webhook endpoints are NOT protected by authentication (public) but validate the request structure.
- Add idempotency: if confirmation is received twice for the same invoice, skip re-processing.

### ✓ Step 5: Implement auto-activation and email notifications
Complete the payment flow by activating the subscription and sending email receipts.
- Add `activateSubscriptionByPayment(invoiceId)` method to `SchoolService` (internal, not exposed as admin API):
  - Load the PaymentInvoice and associated School.
  - Calculate new subscription dates: `startDate` = day after current `subscriptionEndDate` (or today if no active subscription), `endDate` = startDate + billing period duration (for TERM: next 3 months, for ACADEMIC_YEAR: 12 months, configurable).
  - Call existing `SubscriptionTermsResolver` to validate dates.
  - Update School: set `subscriptionTier`, `subscriptionStatus = ACTIVE`, dates, `enabledModules` (from tier defaults), and capacity limits (from `PlanCatalog`).
  - Save school and evict cache in `ModuleAccessService`.
  - Log a PaymentTransaction with SUBSCRIPTION_ACTIVATED event.
- Create `PaymentNotificationService` with `sendPaymentReceipt(invoice, school)`:
  - Use Spring Mail (already in pom.xml) to send an email to the school's admin email.
  - Email includes: invoice number, amount, tier, billing period, new subscription dates, Mpesa transaction ID.
- Integrate notification into the confirmation flow: after subscription activation, send the email receipt.
- Create a simple email template (text-based or HTML) for the receipt.

### ✓ Step 6: Add admin transaction view and integration testing
Complete the payment module with admin visibility and validate the full flow.
- Add admin endpoint to `PaymentController`: `GET /api/v1/payments/admin/transactions` (SUPER_ADMIN) — returns all payment transactions with filtering by school, date range, status.
- Add admin endpoint: `GET /api/v1/payments/admin/invoices` — list all invoices with status filtering.
- Add a `PaymentTransactionDto` for the response.
- Verify the complete flow:
  1. Admin creates subscription prices via API.
  2. School admin initiates payment → invoice created.
  3. Validation callback received → invoice validated.
  4. Confirmation callback received → subscription activated, email sent.
  5. School's tier, modules, and limits updated correctly.
  6. Invoice expires after 24 hours if not paid.
- Ensure all existing subscription tests still pass (no regression in `SchoolServiceImpl`, `ModuleAccessService`, `PlanGuard`).
- Add `@NoModuleRequired` to payment controllers that should be accessible without a specific module entitlement.

### ✓ Step 7: Enhance email notifications with dual dispatch & SMTP configuration
Add super admin audit email and professional HTML templates, configure SMTP via env vars.
- Enhance `PaymentNotificationService` to send two emails on payment confirmation:
  1. School Admin receipt with subscription activation details (HTML template).
  2. Super Admin audit notification with school info, amount, and transaction ID (HTML template).
- Add `findByRole` method to `UserRepository` to fetch super admin email.
- Configure SMTP properties in `application.yaml` using environment variables (`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`).
- Create professional HTML email templates as inline strings in `PaymentNotificationService`.

### ✓ Step 8: Write comprehensive unit tests for Mpesa callback services
Build JUnit 5 + Mockito unit tests for `MpesaCallbackService` and `MpesaWebhookController`.
- Create `MpesaCallbackServiceTest` testing:
  - `handleValidation` with valid/invalid invoice, amount mismatch, already-processed invoice.
  - `handleConfirmation` with valid confirmation, idempotency (duplicate confirmation), failed payment.
  - Verify email service invocation via mock.
- Create `MpesaWebhookControllerTest` testing:
  - Validation endpoint returns 200 OK with correct response.
  - Confirmation endpoint returns 200 OK with correct response.
  - Request body parsing from Mpesa JSON format.

### ✓ Step 9: Write integration tests for the Mpesa confirmation webhook
Build `@SpringBootTest` integration test simulating the full confirmation flow.
- Create `MpesaWebhookIntegrationTest` with `@AutoConfigureMockMvc`:
  - Seed test data: school, subscription price, pending invoice.
  - POST mock confirmation payload to `/api/v1/payments/callback/confirmation`.
  - Assert 200 OK response with `ResultCode: 0`.
  - Assert database state: invoice status = CONFIRMED, school subscription updated.
  - Verify email trigger via `@SpyBean` on `JavaMailSender`.

### ✓ Step 10: Phase 1 — ACCOUNTANT role, default-deny audit, and gate wiring
Build the ACCOUNTANT role foundation with full test coverage.
- Added `ACCOUNTANT` to Role enum with proper creation hierarchy.
- Created `FeesRuleException` and `FeesAccessGuard` for centralized authorization.
- Added `createAccountant()` to `UserRegistrationService` with BILLING module check.
- Added `POST /api/v1/admin/accountants` endpoint.
- Added `/api/v1/fees/callback/**` to `permitAll()` in SecurityConfig.
- 22 unit tests in `FeesPhase1Test`.

### ✓ Step 11: Phase 2 — Fee structures, accounts, invoices, ledger, payments, and statements
Build the core fees data layer, services, controllers, and tests.
- Created 8 entities: FeeStructure, FeeItem, StudentFeeAccount, FeeInvoice, FeeInvoiceLine, LedgerEntry, FeePayment, PaymentAllocation.
- Created 7 enums: FeeInvoiceStatus, LedgerEntryType, FeePaymentStatus, FeePaymentChannel, DiscountType, FeeStructureStatus, StudentFeeAccountStatus.
- Created 7 repositories with proper query methods and PESSIMISTIC_WRITE lock support.
- Created 5 Flyway migrations (V38–V42) with proper FK constraints and indexes.
- Created 8 DTOs for requests and responses.
- Created 5 services: FeeStructureService, FeeInvoiceService, LedgerService, PaymentPostingService, FeeStatementService.
- Created FeesController with 11 endpoints gated by `@RequiresModule(ServiceModule.BILLING)`.
- 14 unit tests in `FeesPhase2Test` covering all services and enums.

### ✓ Step 12: Phase 3 — Parent and student views and permissions
Build parent and student role-scoped fee data access with proper authorization.
- Created `LinkedChildView` DTO for parent child listing (studentId, studentName, admissionNumber, balance).
- Added parent endpoints to `FeesController`: `GET /api/v1/fees/parent/children` (list linked children with balance), `GET /api/v1/fees/parent/{studentId}/invoices`, `GET /api/v1/fees/parent/{studentId}/payments`, `GET /api/v1/fees/parent/{studentId}/statement`.
- Wired `ParentAccessGuard` in all parent endpoints to verify parent-student link before returning data.
- Added student self-service endpoints: `GET /api/v1/fees/student/invoices`, `GET /api/v1/fees/student/payments`, `GET /api/v1/fees/student/statement` — student sees only their own data.
- Enhanced service methods to handle PARENT role with ParentAccessGuard verification.
- 18 unit tests in `FeesPhase3Test` covering role-based access matrix, parent link verification, student self-service, and cross-tenant isolation.

### ✓ Step 13: Phase 4 — School payment config, credential encryption, callback inbox, C2B matching, unallocated queue
Build the school Mpesa payment configuration, encrypted credential storage, durable callback inbox, C2B payment matching by admission number, and unallocated payment resolution queue.
- Created 4 enums: ShortcodeType, MpesaEnvironment, CallbackChannel, UnallocatedPaymentStatus.
- Created 3 entities: SchoolPaymentConfig, CallbackInbox, UnallocatedPayment.
- Created 3 repositories: SchoolPaymentConfigRepository, CallbackInboxRepository, UnallocatedPaymentRepository.
- Created 3 Flyway migrations (V43–V45).
- Created CredentialEncryptionService with AES-GCM encryption/decryption.
- Created SchoolPaymentConfigService for CRUD, test connection, token rotation.
- Created C2bCallbackService for processing C2B confirmation and validation callbacks.
- Created PaymentMatchingService for normalizing and matching account references to students.
- Created UnallocatedPaymentService for listing and resolving unallocated payments.
- Created C2bWebhookController with public callback endpoints (@NoModuleRequired).
- Added config endpoints to FeesController: GET/POST config, test connection, rotate token, toggle enabled.
- Added unallocated payment endpoints: list, allocate, refund.
- Registered CredentialEncryptionService bean in ApplicationConfig.
- 25 unit tests in FeesPhase4Test covering encryption, matching, config, unallocated payments, enums, and DTOs.
- Total: 88 fees tests passing (22 Phase 1 + 14 Phase 2 + 27 Phase 3 + 25 Phase 4).

### ✓ Step 14: Phase 5 — STK Push, callback, status query, and timeout job
Build the STK Push payment flow for parent and student in-app payments.
- Created MpesaClient interface with DarajaMpesaClient implementation and FakeMpesaClient for tests.
- Created StkPushService for initiating STK push payments with validation, idempotency, and rate limiting.
- Created StkCallbackService for processing STK push result callbacks from Safaricom.
- Added STK callback endpoint to C2bWebhookController: POST /api/v1/fees/callback/stk/{token}.
- Added STK initiation endpoint to FeesController: POST /api/v1/fees/payments/stk.
- Added payment status polling endpoint: GET /api/v1/fees/payments/{id}/status.
- Created StkPushTimeoutJob as a scheduled job to resolve stale PENDING payments via status query.
- Created StkPushRequestDto and StkPushResponseDto.
- Added FeePayment repository methods for STK push: findBySchoolIdAndStudentIdAndStatus, findByCheckoutRequestID.
- Updated FeePayment entity with checkoutRequestID field for STK matching.
- Created Flyway migration V46 for adding checkout_request_id to fee_payments.
- 25 unit tests in FeesPhase5Test covering STK initiation, callback processing, idempotency, timeout job, and status queries.
- Total: 113 fees tests passing (22 Phase 1 + 14 Phase 2 + 27 Phase 3 + 25 Phase 4 + 25 Phase 5).

### ✓ Step 15: Phase 6 — Reports, reconciliation, CSV export, and security review
Build the fees reporting layer with collection summaries, arrears by class level, daily reconciliation, CSV export with formula injection protection, and a full security review.
- Created 4 DTOs: CollectionByDayView, CollectionByChannelView, ArrearsByClassLevelView, ReconciliationDayView.
- Created FeeReportService with methods: collectionByDay(), collectionByChannel(), arrearsByClassLevel(), dailyReconciliation().
- Added native SQL queries in FeePaymentRepository (sumSucceededByDay, sumSucceededByChannel), StudentFeeAccountRepository (arrearsByClassLevel), LedgerEntryRepository (sumPaymentsByDay).
- Created CsvExporter utility with formula injection protection (escapes =, +, -, @), row cap (10,000), BOM support for Excel.
- Added 5 report endpoints to FeesController: GET /reports/collection, /reports/collection/by-channel, /reports/arrears, /reports/reconciliation, /reports/export (CSV).
- Security review: verified all report queries are school-scoped, callback endpoints are token-protected, credentials never returned by config endpoints.
- 28 unit tests in FeesPhase6Test covering collection reports (3), channel reports (1), arrears reports (3), reconciliation (3), CSV export (12), and security review (6).
- Total: 141 fees tests passing (22 Phase 1 + 14 Phase 2 + 27 Phase 3 + 25 Phase 4 + 25 Phase 5 + 28 Phase 6).