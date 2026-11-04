package com.peterscode.edutrack.auth.service;

import com.peterscode.edutrack.auth.entity.RefreshToken;
import com.peterscode.edutrack.auth.repository.RefreshTokenRepository;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.jwt.JwtService;
import com.peterscode.edutrack.security.service.CustomUserDetailsService;
import com.peterscode.edutrack.shared.exception.SchoolInactiveException;
import com.peterscode.edutrack.shared.exception.SchoolRequiredException;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final ModuleAccessService moduleAccessService;
    private final Clock clock;

    @Value("${application.security.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @PostConstruct
    void validateRefreshExpiration() {
        if (refreshExpiration < 1_000L) {
            throw new IllegalStateException("Refresh-token expiration must be at least one second");
        }
    }

    @Transactional
    public TokenPair issue(UserPrincipal principal) {
        return createTokenPair(principal, java.util.UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, SchoolInactiveException.class, SchoolRequiredException.class})
    public TokenPair rotate(String presentedToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash(presentedToken))
                .orElseThrow(RefreshTokenService::invalidToken);
        LocalDateTime now = LocalDateTime.now(clock);
        if (stored.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw invalidToken();
        }
        if (!stored.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        UserDetails details = userDetailsService.loadUserByUsername(stored.getUser().getEmail());
        if (!(details instanceof UserPrincipal principal)
                || !details.isEnabled()
                || !details.isAccountNonLocked()) {
            stored.setRevokedAt(now);
            throw invalidToken();
        }
        ensureSchoolIsActive(principal);

        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);
        return createTokenPair(principal, stored.getFamilyId());
    }

    @Transactional
    public void revoke(String presentedToken) {
        refreshTokenRepository.findByTokenHashForUpdate(hash(presentedToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now(clock));
                    refreshTokenRepository.save(token);
                });
    }

    private TokenPair createTokenPair(UserPrincipal principal, String familyId) {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(refreshExpiration / 1_000L);

        RefreshToken token = RefreshToken.builder()
                .user(userRepository.getReferenceById(principal.getId()))
                .tokenHash(hash(rawToken))
                .familyId(familyId)
                .expiresAt(expiresAt)
                .build();
        refreshTokenRepository.save(token);

        return new TokenPair(
                jwtService.generateToken(principal),
                rawToken,
                jwtService.getAccessTokenExpirationSeconds(),
                refreshExpiration / 1_000,
                principal
        );
    }

    private void ensureSchoolIsActive(UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getSchoolId() == null) {
            throw new SchoolRequiredException();
        }
        if (!moduleAccessService.isSchoolActive(principal.getSchoolId())) {
            throw new SchoolInactiveException("Your school account has been deactivated. Please contact support.");
        }
    }

    private static String hash(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static BadCredentialsException invalidToken() {
        return new BadCredentialsException("Invalid refresh token");
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            long refreshTokenExpiresIn,
            UserPrincipal principal
    ) {
    }
}
