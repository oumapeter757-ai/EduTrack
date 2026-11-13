# Transaction Rules

## Isolation Level

The application runs globally at **READ COMMITTED** (set via Hikari `connection-init-sql`).
No per-method `isolation = Isolation.READ_COMMITTED` annotations are needed.

## Where @Transactional Belongs

- **Service boundary**: Every public method that writes data must be `@Transactional`.
- **Read-only**: Query methods that only read should use `@Transactional(readOnly = true)`.
- **Never on private/package-private**: `@Transactional` on non-public methods is ignored by the proxy.
- **Self-invocation**: Calling a `@Transactional` method from within the same class bypasses the proxy. Use a separate bean or `AopContext.currentProxy()` if needed.

## External Calls

**Never** make external calls (M-Pesa, SMTP, HTTP, file I/O, Redis) while holding a DB lock or inside a long transaction.

**Rule**: Commit the DB state first, then call the external service, then commit the result.

Example (STK Push):
1. Save PENDING payment → commit
2. Call MpesaClient (outside transaction)
3. Save result → commit

## Event Listeners

All `@TransactionalEventListener` must use `phase = TransactionPhase.AFTER_COMMIT` to avoid running on data that may be rolled back.

## Audit Rows

Audit rows should be written in the **same transaction** as the action they audit, so they roll back together. Do not use `REQUIRES_NEW` for audit rows.

## Long Transactions

- No loops with unbounded work inside one transaction.
- Scheduled jobs process bounded batches, each in its own transaction.
- Report generation should stream results, not load everything in one transaction.

## REQUIRES_NEW

Only use `REQUIRES_NEW` when the inner operation must survive an outer rollback (e.g., logging an error to a separate table). Audit rows should NOT use this.
