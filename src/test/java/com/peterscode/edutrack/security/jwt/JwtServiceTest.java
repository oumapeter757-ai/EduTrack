package com.peterscode.edutrack.security.jwt;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",
                Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 60_000L);
        principal = UserPrincipal.builder()
                .id(1L)
                .email("user@example.test")
                .role(Role.SCHOOL_ADMIN)
                .authorities(List.of())
                .active(true)
                .build();
    }

    @Test
    void generatesAndParsesSignedClaims() {
        String token = jwtService.generateToken(
                Map.of("userId", 1L, "role", "ROLE_SCHOOL_ADMIN"),
                principal);

        assertThat(jwtService.extractUsername(token)).isEqualTo(principal.getUsername());
        Integer userId = jwtService.<Integer>extractClaim(token,
                claims -> claims.get("userId", Integer.class));
        assertThat(userId).isEqualTo(1);
        assertThat(jwtService.isTokenValid(token, principal)).isTrue();
    }

    @Test
    void rejectsTokenSignedByAnotherKey() {
        String token = jwtService.generateToken(Map.of(), principal);
        JwtService otherService = new JwtService();
        ReflectionTestUtils.setField(otherService, "secretKey",
                Base64.getEncoder().encodeToString(new byte[]{1, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));

        assertThatThrownBy(() -> otherService.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }
}
