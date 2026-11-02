package com.peterscode.edutrack.cache.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for EduTrack Redis caching.
 * <p>
 * Driven by {@code edutrack.cache.*} YAML properties.
 * A master toggle {@code enabled} controls whether Redis caching is active.
 * When disabled, a no-op cache manager is used and all requests fall through to the database.
 */
@Component
@ConfigurationProperties(prefix = "edutrack.cache")
@Validated
@Getter
@Setter
public class EdutrackCacheProperties {

    /** Master toggle: when false, Redis caching is disabled entirely. */
    private boolean enabled = false;

    /** Schema version embedded in cache key prefixes so deploys that change DTO shapes bump the version. */
    private int schemaVersion = 1;

    /** Per-cache TTL configuration. Key name matches the cache name used in @Cacheable. */
    private Map<String, Duration> ttl = new HashMap<>();

    /** Default TTL applied to caches that don't have an explicit entry. */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /**
     * Default constructor for Spring Boot binding.
     * Populates sensible defaults for all known cache names.
     */
    public EdutrackCacheProperties() {
        // Public school lookup — short TTL because school profile changes are rare but should reflect quickly
        ttl.put("school-public", Duration.ofMinutes(5));
        // Authenticated school profile
        ttl.put("school-profile", Duration.ofMinutes(2));
        // Configuration data — class levels, streams, periods, rooms, curriculum, subjects
        ttl.put("classlevels", Duration.ofMinutes(10));
        ttl.put("streams", Duration.ofMinutes(10));
        ttl.put("periods", Duration.ofMinutes(10));
        ttl.put("rooms", Duration.ofMinutes(10));
        ttl.put("curriculum", Duration.ofMinutes(10));
        ttl.put("subjects", Duration.ofMinutes(10));
        // Grading configuration
        ttl.put("gradescales", Duration.ofMinutes(10));
        ttl.put("assessmenttypes", Duration.ofMinutes(10));
        ttl.put("assessmentpapers", Duration.ofMinutes(10));
        // Library configuration
        ttl.put("library-categories", Duration.ofMinutes(10));
        ttl.put("borrowing-policy", Duration.ofMinutes(30));
        // Fee structure configuration
        ttl.put("fee-structure", Duration.ofMinutes(10));
        // Published timetable views — longer TTL while published
        ttl.put("timetable-teacher", Duration.ofHours(1));
        ttl.put("timetable-student", Duration.ofHours(1));
    }
}
