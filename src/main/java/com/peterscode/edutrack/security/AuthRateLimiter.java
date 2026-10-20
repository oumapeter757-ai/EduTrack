package com.peterscode.edutrack.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.peterscode.edutrack.shared.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {
    private final Cache<String, Attempt> attempts;
    private final Cache<String, Attempt> signupAttempts;
    private final Cache<String, Attempt> publicLookupAttempts;
    private final int loginEmailMaxFailures;
    private final int loginIpMaxFailures;
    private final int signupIpMaxAttempts;
    private final int publicLookupMaxAttempts;
    private final long windowSeconds;
    private final long signupWindowSeconds;
    private final boolean trustedProxy;

    public AuthRateLimiter(
            @Value("${edutrack.auth.rate-limit.login-email-max-failures:5}") int loginEmailMaxFailures,
            @Value("${edutrack.auth.rate-limit.login-ip-max-failures:30}") int loginIpMaxFailures,
            @Value("${edutrack.auth.rate-limit.signup-ip-max-attempts:5}") int signupIpMaxAttempts,
            @Value("${edutrack.auth.rate-limit.window-seconds:900}") long windowSeconds,
            @Value("${edutrack.auth.rate-limit.signup-window-seconds:3600}") long signupWindowSeconds,
            @Value("${edutrack.auth.rate-limit.public-lookup-max-attempts:30}") int publicLookupMaxAttempts,
            @Value("${edutrack.auth.rate-limit.trusted-proxy:false}") boolean trustedProxy) {
        this.loginEmailMaxFailures = loginEmailMaxFailures;
        this.loginIpMaxFailures = loginIpMaxFailures;
        this.signupIpMaxAttempts = signupIpMaxAttempts;
        this.windowSeconds = windowSeconds;
        this.signupWindowSeconds = signupWindowSeconds;
        this.trustedProxy = trustedProxy;
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)
                .build();
        this.signupAttempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(signupWindowSeconds))
                .maximumSize(100_000)
                .build();
        this.publicLookupAttempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)
                .build();
        this.publicLookupMaxAttempts = publicLookupMaxAttempts;
    }

    public void check(String email) {
        checkLogin(email);
    }

    public void checkLogin(String email) {
        checkKey("ip:" + clientIp(), loginIpMaxFailures);
        checkKey("email:" + normalize(email), loginEmailMaxFailures);
    }

    public void checkSignup(String email) {
        checkKey(signupAttempts, "signup-ip:" + clientIp(), signupIpMaxAttempts, signupWindowSeconds);
    }

    public void checkPublicLookup() {
        String key = "public-ip:" + clientIp();
        checkKey(publicLookupAttempts, key, publicLookupMaxAttempts, windowSeconds);
        increment(publicLookupAttempts, key);
    }

    public void failure(String email) {
        increment(signupAttempts, "signup-ip:" + clientIp());
        try {
            Thread.sleep(25L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public void success(String email) {
        attempts.invalidate("ip:" + clientIp());
        attempts.invalidate("email:" + normalize(email));
        signupAttempts.invalidate("signup-ip:" + clientIp());
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        increment("ip:" + clientIp());
        increment("email:" + normalize(email));
        delay();
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        success(authentication.getName());
    }

    private void checkKey(String key, int maxFailures) {
        checkKey(attempts, key, maxFailures, windowSeconds);
    }

    private void checkKey(Cache<String, Attempt> cache, String key, int maxFailures, long retryAfterSeconds) {
        Attempt attempt = cache.getIfPresent(key);
        if (attempt != null && attempt.count().get() >= maxFailures) {
            throw new TooManyRequestsException(retryAfterSeconds);
        }
    }

    private void delay() {
        try {
            Thread.sleep(25L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void increment(String key) {
        increment(attempts, key);
    }

    private void increment(Cache<String, Attempt> cache, String key) {
        cache.get(key, ignored -> new Attempt(new AtomicInteger())).count().incrementAndGet();
    }

    private String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "unknown";
        HttpServletRequest request = attributes.getRequest();
        if (trustedProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempt(AtomicInteger count) {}
}
