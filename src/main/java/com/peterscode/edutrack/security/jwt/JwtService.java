package com.peterscode.edutrack.security.jwt;

import com.peterscode.edutrack.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String privateKeyPem;

    @Value("${application.security.jwt.public-key:}")
    private String publicKeyPem;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    private volatile PublicKey publicKey;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserPrincipal userPrincipal) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", userPrincipal.getId());
        extraClaims.put("schoolId", userPrincipal.getSchoolId());
        extraClaims.put("role", userPrincipal.getAuthorities().iterator().next().getAuthority());
        extraClaims.put("userVersion", userPrincipal.getUserVersion());

        return generateToken(extraClaims, userPrincipal);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + jwtExpiration);
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        final Date expiration = extractExpiration(token);
        final Number tokenVersion = extractClaim(token, claims -> claims.get("userVersion", Number.class));
        boolean isUsernameMatch = username.equals(userDetails.getUsername());
        boolean isNotExpired = expiration.after(new Date());
        boolean isCurrentUserVersion = userDetails instanceof UserPrincipal principal
                && tokenVersion != null
                && tokenVersion.longValue() == principal.getUserVersion();
        return isUsernameMatch && isNotExpired && isCurrentUserVersion;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtExpiration / 1_000;
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private PrivateKey getPrivateKey() {
        try {
            String cleaned = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Decoders.BASE64.decode(cleaned);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load RSA private key for JWT signing", e);
        }
    }

    private PublicKey getPublicKey() {
        if (this.publicKey != null) {
            return this.publicKey;
        }
        synchronized (this) {
            if (this.publicKey != null) {
                return this.publicKey;
            }
            PublicKey pub;
            if (publicKeyPem != null && !publicKeyPem.isBlank()) {
                // Public key provided via config — use it for verification
                try {
                    String cleaned = publicKeyPem
                            .replace("-----BEGIN PUBLIC KEY-----", "")
                            .replace("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s", "");
                    byte[] keyBytes = Decoders.BASE64.decode(cleaned);
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
                    pub = keyFactory.generatePublic(keySpec);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to load RSA public key for JWT verification", e);
                }
            } else {
                // Derive public key from the private key (single-instance fallback)
                try {
                    String cleaned = privateKeyPem
                            .replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                            .replace("-----END RSA PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
                    byte[] keyBytes = Decoders.BASE64.decode(cleaned);
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                    RSAPrivateCrtKey priv = (RSAPrivateCrtKey) keyFactory.generatePrivate(keySpec);
                    // Reconstruct the public key from the private key's modulus and exponent
                    java.security.spec.RSAPublicKeySpec pubSpec = new java.security.spec.RSAPublicKeySpec(
                            priv.getModulus(), priv.getPublicExponent());
                    pub = keyFactory.generatePublic(pubSpec);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to derive RSA public key from private key", e);
                }
            }
            this.publicKey = pub;
            return pub;
        }
    }
}