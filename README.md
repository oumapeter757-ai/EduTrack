# EduTrack

## Running EduTrack locally

1. Copy `.env.example` to `.env`. Fill in local MySQL credentials, JWT secret, and M-Pesa sandbox configuration. Keep `.env` private and untracked.
2. Start MySQL 9.7: `docker compose up -d mysql`
3. Wait for the database health check: `docker compose ps`
4. Run the application: `./mvnw spring-boot:run`
5. Check application health: `curl --fail http://localhost:8080/actuator/health`

The database is exposed only on `127.0.0.1:3307`. Flyway runs at application startup. Before upgrading an existing local database, back it up and inspect `flyway_schema_history`; V47 is an immutable historical migration, and a previous failed run can leave MySQL DDL partially applied. Never use the local profile against shared, staging, or production data.

## Notifications

Email and SMS are disabled by default. SMS modes are `DISABLED`, `SANDBOX`, and `LIVE`; never enable live delivery until operator-approved credentials, a consented test recipient, and a positive per-tier quota are configured. The Africa's Talking delivery-report payload/authentication contract is not verified, so no delivery-report route is enabled.

For the one manual live test only, configure `EDUTRACK_NOTIFICATION_SMS_ENABLED=true`, `EDUTRACK_NOTIFICATION_SMS_MODE=LIVE`, `EDUTRACK_NOTIFICATION_SMS_USERNAME`, `EDUTRACK_NOTIFICATION_SMS_API_KEY`, `EDUTRACK_NOTIFICATION_SMS_SENDER_ID`, a positive quota for the school's tier, and a guardian with a consented test number. Start the app, set `ADMIN_JWT`, `TERM_ID`, `STREAM_ID`, and `ABSENT_STUDENT_ID` in the local environment, then submit an absence batch:

```sh
curl --fail-with-body --request POST http://localhost:8080/api/v1/attendance/batch \
  --header "Authorization: Bearer ${ADMIN_JWT}" \
  --header 'Content-Type: application/json' \
  --data "{\"termId\":${TERM_ID},\"streamId\":${STREAM_ID},\"attendanceDate\":\"$(date +%F)\",\"attendanceType\":\"DAILY\",\"exceptions\":[{\"studentId\":${ABSENT_STUDENT_ID},\"status\":\"ABSENT\"}]}"
```

Only run this manually against the local app after confirming the resolved guardian destination is the consented test number. Never enable `LIVE` in CI or on a shared environment, and do not put credentials or a real number in shell history.
