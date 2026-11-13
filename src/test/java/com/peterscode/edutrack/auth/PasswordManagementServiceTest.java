package com.peterscode.edutrack.auth;

import com.peterscode.edutrack.auth.entity.PasswordResetToken;
import com.peterscode.edutrack.auth.repository.PasswordResetTokenRepository;
import com.peterscode.edutrack.auth.repository.RefreshTokenRepository;
import com.peterscode.edutrack.auth.service.PasswordManagementService;
import com.peterscode.edutrack.notification.provider.EmailProvider;
import com.peterscode.edutrack.security.AuthRateLimiter;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailProvider emailProvider;
    @Mock
    private AuthRateLimiter rateLimiter;

    private PasswordManagementService service;
    private User user;
    private UserPrincipal schoolAdmin;

    @BeforeEach
    void setUp() {
        service = new PasswordManagementService(
                userRepository,
                resetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailProvider,
                rateLimiter,
                Clock.fixed(LocalDateTime.of(2026, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "resetExpirationMinutes", 30L);
        ReflectionTestUtils.setField(service, "resetBaseUrl", "https://app.example.test/reset-password");

        user = User.builder()
                .id(20L)
                .schoolId(3L)
                .email("user@example.test")
                .password("old-hash")
                .active(true)
                .role(Role.TEACHER)
                .build();
        schoolAdmin = UserPrincipal.builder()
                .id(10L)
                .email("admin@example.test")
                .schoolId(3L)
                .role(Role.SCHOOL_ADMIN)
                .active(true)
                .authorities(List.of())
                .build();
    }

    @Test
    void forgotPasswordReturnsSameMessageForUnknownEmail() throws Exception {
        when(emailProvider.isEnabled()).thenReturn(true);
        when(userRepository.findByEmailForUpdate("missing@example.test")).thenReturn(Optional.empty());

        String message = service.requestPasswordReset(" Missing@Example.Test ");

        assertThat(message).contains("If an account exists");
        verify(userRepository).findByEmailForUpdate("missing@example.test");
        verifyNoInteractions(resetTokenRepository);
        verify(emailProvider, never()).send(any());
    }

    @Test
    void forgotPasswordEmailsRawTokenButPersistsOnlyItsHash() throws Exception {
        when(emailProvider.isEnabled()).thenReturn(true);
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(resetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> {
            PasswordResetToken token = invocation.getArgument(0);
            token.setId(42L);
            return token;
        });

        String message = service.requestPasswordReset(user.getEmail());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken persisted = tokenCaptor.getValue();
        assertThat(persisted.getTokenHash()).hasSize(64);
        assertThat(persisted.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 30));

        ArgumentCaptor<EmailProvider.EmailDelivery> emailCaptor =
                ArgumentCaptor.forClass(EmailProvider.EmailDelivery.class);
        verify(emailProvider).send(emailCaptor.capture());
        Matcher tokenMatcher = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)")
                .matcher(emailCaptor.getValue().body());
        assertThat(tokenMatcher.find()).isTrue();
        String rawToken = tokenMatcher.group(1);
        assertThat(persisted.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(message).contains("If an account exists");
    }

    @Test
    void resetPasswordConsumesTokenAndRevokesExistingRefreshTokens() {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash("stored-hash")
                .expiresAt(LocalDateTime.of(2026, 1, 1, 0, 30))
                .build();
        when(resetTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.resetPassword("valid-token", "new-password");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(resetTokenRepository).invalidateUnusedForUser(20L, LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(refreshTokenRepository).revokeAllForUser(20L, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(resetTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("unknown-token", "new-password"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired password reset token");

        verifyNoInteractions(passwordEncoder, refreshTokenRepository);
    }

    @Test
    void ownPasswordChangeRequiresCurrentPasswordAndRevokesRefreshTokens() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .schoolId(user.getSchoolId())
                .role(user.getRole())
                .active(true)
                .authorities(List.of())
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword(principal, "wrong-password", "new-password"))
                .isInstanceOf(BadCredentialsException.class);
        verify(passwordEncoder, never()).encode(anyString());

        when(passwordEncoder.matches("correct-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        service.changeOwnPassword(principal, "correct-password", "new-password");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(refreshTokenRepository).revokeAllForUser(20L, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void schoolAdminCannotChangePasswordOutsideOwnSchool() {
        when(userRepository.findBySchoolIdAndId(3L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adminChangePassword(schoolAdmin, 99L, "new-password"))
                .isInstanceOf(ResponseStatusException.class);

        verify(userRepository).findBySchoolIdAndId(3L, 99L);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void nonAdminCannotChangeAnotherUsersPassword() {
        UserPrincipal teacher = UserPrincipal.builder()
                .id(20L)
                .email(user.getEmail())
                .schoolId(3L)
                .role(Role.TEACHER)
                .active(true)
                .authorities(List.of())
                .build();

        assertThatThrownBy(() -> service.adminChangePassword(teacher, 99L, "new-password"))
                .isInstanceOf(UnauthorizedOperationException.class);
        verifyNoInteractions(passwordEncoder);
    }
}
