package com.peterscode.edutrack.security.jwt;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private UserPrincipal principal;
    private String privateKeyPem;
    private String publicKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        privateKeyPem = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        publicKeyPem = toPem("PUBLIC KEY", keyPair.getPublic().getEncoded());

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "privateKeyPem", privateKeyPem);
        ReflectionTestUtils.setField(jwtService, "publicKeyPem", publicKeyPem);
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
    void rejectsTokenSignedByAnotherKey() throws Exception {
        String token = jwtService.generateToken(Map.of(), principal);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair otherKeyPair = keyGen.generateKeyPair();
        String otherPrivatePem = toPem("PRIVATE KEY", otherKeyPair.getPrivate().getEncoded());
        String otherPublicPem = toPem("PUBLIC KEY", otherKeyPair.getPublic().getEncoded());

        JwtService otherService = new JwtService();
        ReflectionTestUtils.setField(otherService, "privateKeyPem", otherPrivatePem);
        ReflectionTestUtils.setField(otherService, "publicKeyPem", otherPublicPem);
        ReflectionTestUtils.setField(otherService, "jwtExpiration", 60_000L);

        assertThatThrownBy(() -> otherService.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    private String toPem(String type, byte[] keyBytes) {
        java.util.Base64.Encoder encoder = java.util.Base64.getMimeEncoder(64, "\n".getBytes());
        return "-----BEGIN " + type + "-----\n"
                + encoder.encodeToString(keyBytes)
                + "\n-----END " + type + "-----\n";
    }
}
