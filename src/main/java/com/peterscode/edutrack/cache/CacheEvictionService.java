package com.peterscode.edutrack.cache;

import com.peterscode.edutrack.cache.config.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for evicting Redis cache entries.
 * <p>
 * Used by the admin eviction endpoint and by programmatic eviction after writes.
 * Uses SCAN instead of KEYS to avoid blocking Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "edutrack.cache", name = "enabled", havingValue = "true")
public class CacheEvictionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheKeys cacheKeys;

    /**
     * Evict all cached data for a specific school.
     *
     * @param schoolId the school identifier
     * @return number of keys evicted
     */
    public long evictAllForSchool(Long schoolId) {
        if (schoolId == null) {
            log.warn("Cannot evict cache: schoolId is null");
            return 0;
        }

        String prefix = cacheKeys.scanPrefixAll(schoolId);
        return scanAndDelete(prefix);
    }

    /**
     * Evict all cached data for a specific cache and school.
     *
     * @param cacheName the cache name (e.g., "school-profile")
     * @param schoolId  the school identifier
     * @return number of keys evicted
     */
    public long evictCacheForSchool(String cacheName, Long schoolId) {
        if (schoolId == null) {
            log.warn("Cannot evict cache '{}': schoolId is null", cacheName);
            return 0;
        }

        String prefix = cacheKeys.scanPrefix(cacheName, schoolId);
        return scanAndDelete(prefix);
    }

    /**
     * Evict a specific cache key.
     *
     * @param key the full cache key
     * @return true if the key was deleted
     */
    public boolean evictKey(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Evict all keys matching a prefix using SCAN (non-blocking).
     *
     * @param prefix the key prefix
     * @return number of keys deleted
     */
    private long scanAndDelete(String prefix) {
        long count = 0;
        try {
            ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(100).build();
            List<String> keys = new ArrayList<>();
            try (var cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            for (String key : keys) {
                Boolean deleted = redisTemplate.delete(key);
                if (Boolean.TRUE.equals(deleted)) {
                    count++;
                }
            }
            log.info("Evicted {} cache keys matching prefix: {}", count, prefix);
        } catch (Exception e) {
            log.warn("Error evicting cache keys with prefix '{}': {}", prefix, e.getClass().getSimpleName());
        }
        return count;
    }
}
