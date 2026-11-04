package com.peterscode.edutrack.auth;

import com.peterscode.edutrack.auth.entity.RefreshToken;
import com.peterscode.edutrack.auth.repository.RefreshTokenRepository;
import com.peterscode.edutrack.auth.service.RefreshTokenService;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.jwt.JwtService;
import com.peterscode.edutrack.security.service.CustomUserDetailsService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private JwtService jwtService;
    @Mock
    private ModuleAccessService moduleAccessService;

    private RefreshTokenService refreshTokenService;
    private UserPrincipal principal;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                userDetailsService,
                jwtService,
                moduleAccessService,
                Clock.fixed(LocalDateTime.of(2026, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", 604_800_000L);
        principal = UserPrincipal.builder()
                .id(7L)
                .email("admin@example.test")
                .schoolId(3L)
                .role(Role.SCHOOL_ADMIN)
                .active(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")))
                .build();
        user = User.builder().id(7L).email(principal.getUsername()).build();
    }

    @Test
    void issueStoresOnlyHashAndReturnsOpaqueRefreshToken() {
        stubIssue();
        RefreshTokenService.TokenPair pair = refreshTokenService.issue(principal);

        assertThat(pair.accessToken()).isEqualTo("access-token");
        assertThat(pair.refreshToken()).hasSize(43).isNotEqualTo("access-token");
        ArgumentCaptor<RefreshToken> storedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(storedToken.capture());
        assertThat(storedToken.getValue().getTokenHash()).hasSize(64)
                .isNotEqualTo(pair.refreshToken());
        assertThat(storedToken.getValue().getExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 1, 8, 0, 0));
    }

    @Test
    void rotateRevokesOldTokenAndIssuesTokenInSameFamily() {
        stubIssue();
        String oldToken = "existing-refresh-token";
        String familyId = UUID.randomUUID().toString();
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash("stored-hash")
                .familyId(familyId)
                .expiresAt(LocalDateTime.of(2026, 1, 8, 0, 0))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));
        when(userDetailsService.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        when(moduleAccessService.isSchoolActive(3L)).thenReturn(true);

        RefreshTokenService.TokenPair pair = refreshTokenService.rotate(oldToken);

        assertThat(stored.getRevokedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(pair.principal()).isSameAs(principal);
        ArgumentCaptor<RefreshToken> issuedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(issuedToken.capture());
        assertThat(issuedToken.getAllValues().get(1).getFamilyId()).isEqualTo(familyId);
        assertThat(issuedToken.getAllValues().get(1).getTokenHash()).isNotEqualTo(oldToken);
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void rotateReplayRevokesRemainingTokensInFamily() {
        String familyId = UUID.randomUUID().toString();
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash("stored-hash")
                .familyId(familyId)
                .expiresAt(LocalDateTime.of(2026, 1, 8, 0, 0))
                .revokedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> refreshTokenService.rotate("replayed-token"))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository).revokeFamily(familyId, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private void stubIssue() {
        when(userRepository.getReferenceById(anyLong())).thenReturn(user);
        when(jwtService.generateToken(principal)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    }
}
