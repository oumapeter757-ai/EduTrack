package com.peterscode.edutrack.auth.service;

import com.peterscode.edutrack.auth.entity.PasswordResetToken;
import com.peterscode.edutrack.auth.repository.PasswordResetTokenRepository;
import com.peterscode.edutrack.auth.repository.RefreshTokenRepository;
import com.peterscode.edutrack.notification.provider.DeliveryFailureException;
import com.peterscode.edutrack.notification.provider.EmailProvider;
import com.peterscode.edutrack.security.AuthRateLimiter;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordManagementService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String RESET_REQUEST_ACCEPTED =
            "If an account exists for that email, a password reset link will be sent.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailProvider emailProvider;
    private final AuthRateLimiter rateLimiter;
    private final Clock clock;

    @Value("${edutrack.auth.password-reset.expiration-minutes:30}")
    private long resetExpirationMinutes;

    @Value("${edutrack.auth.password-reset.base-url:}")
    private String resetBaseUrl;

    @PostConstruct
    void validateConfiguration() {
        if (resetExpirationMinutes < 5 || resetExpirationMinutes > 1440) {
            throw new IllegalStateException("Password reset expiration must be between 5 and 1440 minutes");
        }
        if (resetBaseUrl != null && !resetBaseUrl.isBlank()) {
            URI uri = URI.create(resetBaseUrl);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean localDevelopment = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (!uri.isAbsolute() || uri.getHost() == null || (!secure && !localDevelopment)) {
                throw new IllegalStateException(
                        "Password reset base URL must use HTTPS (HTTP is allowed only for localhost)");
            }
        }
    }

    @Transactional
    public String requestPasswordReset(String suppliedEmail) {
        String email = normalizeEmail(suppliedEmail);
        rateLimiter.checkPasswordReset(email);

        if (!emailProvider.isEnabled() || resetBaseUrl == null || resetBaseUrl.isBlank()) {
            log.warn("Password reset is unavailable because email delivery or the reset URL is not configured");
            return RESET_REQUEST_ACCEPTED;
        }

        Optional<User> candidate = userRepository.findByEmailForUpdate(email);
        if (candidate.isEmpty()) {
            return RESET_REQUEST_ACCEPTED;
        }

        User user = candidate.get();
        LocalDateTime now = LocalDateTime.now(clock);
        resetTokenRepository.invalidateUnusedForUser(user.getId(), now);

        String rawToken = generateToken();
        PasswordResetToken resetToken = resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(now.plusMinutes(resetExpirationMinutes))
                .build());

        String resetUrl = UriComponentsBuilder.fromUriString(resetBaseUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
        try {
            emailProvider.send(new EmailProvider.EmailDelivery(
                    user.getEmail(),
                    "Reset your EduTrack password",
                    "Use this link to reset your EduTrack password. It expires in "
                            + resetExpirationMinutes + " minutes:\n\n" + resetUrl
                            + "\n\nIf you did not request this change, you can ignore this email.",
                    false,
                    "password-reset-" + resetToken.getId()));
        } catch (DeliveryFailureException exception) {
            log.warn("Password reset email delivery failed with code {}", exception.getErrorCode());
            if (!exception.isOutcomeUnknown()) {
                resetToken.setUsedAt(now);
            }
        }
        return RESET_REQUEST_ACCEPTED;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(PasswordManagementService::invalidResetToken);
        LocalDateTime now = LocalDateTime.now(clock);
        if (resetToken.getUsedAt() != null || !resetToken.getExpiresAt().isAfter(now)) {
            throw invalidResetToken();
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetTokenRepository.invalidateUnusedForUser(user.getId(), now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
    }

    @Transactional
    public void changeOwnPassword(UserPrincipal principal, String currentPassword, String newPassword) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedOperationException("User account is unavailable"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        updatePassword(user, newPassword);
    }

    @Transactional
    public void adminChangePassword(UserPrincipal administrator, Long userId, String newPassword) {
        if (administrator == null || (administrator.getRole() != Role.SUPER_ADMIN
                && administrator.getRole() != Role.SCHOOL_ADMIN)) {
            throw new UnauthorizedOperationException("Only administrators can change another user's password");
        }
        if (Objects.equals(administrator.getId(), userId)) {
            throw new UnauthorizedOperationException("Use the change-password endpoint for your own account");
        }

        User target;
        if (administrator.getRole() == Role.SUPER_ADMIN) {
            target = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        } else {
            Long schoolId = administrator.getSchoolId();
            if (schoolId == null) {
                throw new UnauthorizedOperationException("School context is required");
            }
            target = userRepository.findBySchoolIdAndId(schoolId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }
        updatePassword(target, newPassword);
    }

    private void updatePassword(User user, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetTokenRepository.invalidateUnusedForUser(user.getId(), now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateToken() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String hash(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw invalidResetToken();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ResponseStatusException invalidResetToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token");
    }
}
