package com.peterscode.edutrack.cache.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized cache key builder for EduTrack Redis caching.
 * <p>
 * Every key follows the format: {@code edutrack:{env}:v{schemaVersion}:{cacheName}:{schoolId}:{...}}
 * Keys without a schoolId are only allowed for explicitly global, non-tenant data.
 * <p>
 * This class enforces the key format and prevents ad-hoc string keys that could
 * cause tenant data leaks.
 */
@Component
public class CacheKeys {

    private static final String PREFIX = "edutrack";

    private final String env;
    private final int schemaVersion;

    public CacheKeys(
            @Value("${spring.profiles.active:dev}") String env,
            EdutrackCacheProperties properties) {
        this.env = env;
        this.schemaVersion = properties.getSchemaVersion();
    }

    /**
     * Build a tenant-scoped cache key.
     *
     * @param cacheName logical cache name (e.g., "school-profile", "classlevels")
     * @param schoolId  the tenant school identifier
     * @return formatted key string
     */
    public String tenant(String cacheName, Long schoolId) {
        if (schoolId == null) {
            throw new IllegalArgumentException("schoolId must not be null for tenant-scoped cache: " + cacheName);
        }
        return String.format("%s:%s:v%d:%s:%s", PREFIX, env, schemaVersion, cacheName, schoolId);
    }

    /**
     * Build a tenant-scoped cache key with an additional identifier.
     *
     * @param cacheName logical cache name
     * @param schoolId  the tenant school identifier
     * @param id        additional identifier (e.g., classLevelId, streamId, feeStructureId)
     * @return formatted key string
     */
    public String tenant(String cacheName, Long schoolId, Long id) {
        if (schoolId == null) {
            throw new IllegalArgumentException("schoolId must not be null for tenant-scoped cache: " + cacheName);
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null for tenant-scoped cache: " + cacheName);
        }
        return String.format("%s:%s:v%d:%s:%s:%s", PREFIX, env, schemaVersion, cacheName, schoolId, id);
    }

    /**
     * Build a tenant-scoped cache key with two additional identifiers.
     *
     * @param cacheName logical cache name
     * @param schoolId  the tenant school identifier
     * @param id1       first additional identifier (e.g., teacherId, streamId)
     * @param id2       second additional identifier (e.g., termId)
     * @return formatted key string
     */
    public String tenant(String cacheName, Long schoolId, Long id1, Long id2) {
        if (schoolId == null) {
            throw new IllegalArgumentException("schoolId must not be null for tenant-scoped cache: " + cacheName);
        }
        if (id1 == null) {
            throw new IllegalArgumentException("id1 must not be null for tenant-scoped cache: " + cacheName);
        }
        if (id2 == null) {
            throw new IllegalArgumentException("id2 must not be null for tenant-scoped cache: " + cacheName);
        }
        return String.format("%s:%s:v%d:%s:%s:%s:%s", PREFIX, env, schemaVersion, cacheName, schoolId, id1, id2);
    }

    /**
     * Build a tenant-scoped cache key with a string sub-key.
     *
     * @param cacheName logical cache name
     * @param schoolId  the tenant school identifier
     * @param subKey    string sub-key (e.g., borrowerType, subdomain)
     * @return formatted key string
     */
    public String tenant(String cacheName, Long schoolId, String subKey) {
        if (schoolId == null) {
            throw new IllegalArgumentException("schoolId must not be null for tenant-scoped cache: " + cacheName);
        }
        if (subKey == null || subKey.isBlank()) {
            throw new IllegalArgumentException("subKey must not be blank for tenant-scoped cache: " + cacheName);
        }
        return String.format("%s:%s:v%d:%s:%s:%s", PREFIX, env, schemaVersion, cacheName, schoolId, subKey);
    }

    /**
     * Build a global cache key (no schoolId). Use only for explicitly global, non-tenant data.
     * The subdomain or code acts as the tenant identifier in the key.
     *
     * @param cacheName logical cache name (e.g., "school-public")
     * @param identifier global identifier (e.g., subdomain, school code)
     * @return formatted key string
     */
    public String global(String cacheName, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank for global cache: " + cacheName);
        }
        return String.format("%s:%s:v%d:%s:%s", PREFIX, env, schemaVersion, cacheName, identifier);
    }

    /**
     * Return the base prefix for scanning keys of a specific cache.
     * Useful for admin eviction operations.
     *
     * @param cacheName logical cache name
     * @param schoolId  the tenant school identifier
     * @return prefix pattern for SCAN
     */
    public String scanPrefix(String cacheName, Long schoolId) {
        return String.format("%s:%s:v%d:%s:%s", PREFIX, env, schemaVersion, cacheName, schoolId);
    }

    /**
     * Return the base prefix for scanning all keys of a school.
     *
     * @param schoolId the tenant school identifier
     * @return prefix pattern for SCAN
     */
    public String scanPrefixAll(Long schoolId) {
        return String.format("%s:%s:v%d:*:%s", PREFIX, env, schemaVersion, schoolId);
    }
}
