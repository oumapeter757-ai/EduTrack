# Password management

## API

- `POST /api/v1/auth/forgot-password` accepts `{"email":"user@example.com"}`. It always returns the same generic message whether or not an account exists.
- `POST /api/v1/auth/reset-password` accepts `{"token":"...","newPassword":"..."}`. Reset tokens are one-time, expire after 30 minutes by default, and are stored only as SHA-256 hashes.
- `POST /api/v1/auth/change-password` requires a bearer token and accepts `{"currentPassword":"...","newPassword":"..."}`. The current password must be correct.
- `PATCH /api/v1/admin/users/{userId}/password` accepts `{"newPassword":"..."}` and is restricted to administrators. A `SUPER_ADMIN` can change any user's password; a `SCHOOL_ADMIN` can change passwords only for users in their own school. Administrators must use the self-service route for their own password.

All new passwords must be 8–128 characters. Changing or resetting a password revokes the user's refresh tokens and increments the user version, invalidating already-issued access tokens.

## Email configuration

Forgot-password delivery requires a real SMTP email provider and an HTTPS reset-page URL. Configure these environment variables:

```text
EDUTRACK_NOTIFICATION_EMAIL_ENABLED=true
EDUTRACK_NOTIFICATION_EMAIL_PROVIDER=smtp
EDUTRACK_NOTIFICATION_EMAIL_FROM=<verified sender address>
EDUTRACK_AUTH_PASSWORD_RESET_BASE_URL=https://<frontend-host>/reset-password
```

Configure the existing Spring Mail settings (`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD`) and the notification destination encryption key as required by the existing SMTP provider configuration. Do not use the fake or disabled email provider in production. The password reset page should read the `token` query parameter and submit it to the reset endpoint.

`EDUTRACK_AUTH_PASSWORD_RESET_EXPIRATION_MINUTES` can set token lifetime from 5 to 1440 minutes. Public reset requests are rate-limited by client IP and a hash of the normalized email address.

Flyway migration `V69` creates the password-reset token table automatically.
