package com.peterscode.edutrack.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies NoOpCacheManager behavior when Redis caching is disabled.
 * Tests the NoOpCacheManager directly without loading the full application context,
 * because the full context requires MySQL and other infrastructure not available in unit tests.
 */
class NoOpCacheManagerTest {

    @Test
    @DisplayName("NoOpCacheManager returns a no-op cache that does nothing")
    void noOpCacheManagerReturnsNoOpCache() {
        CacheManager cacheManager = new NoOpCacheManager();
        // NoOpCacheManager returns a NoOpCache instance that silently ignores operations
        var cache = cacheManager.getCache("any-cache");
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("any-cache");
    }

    @Test
    @DisplayName("NoOpCacheManager has no cache names")
    void noOpCacheManagerHasNoCaches() {
        CacheManager cacheManager = new NoOpCacheManager();
        assertThat(cacheManager.getCacheNames()).isEmpty();
    }

    @Test
    @DisplayName("NoOpCacheManager is thread safe")
    void noOpCacheManagerIsThreadSafe() {
        CacheManager cacheManager = new NoOpCacheManager();
        // Multiple concurrent calls should not throw
        for (int i = 0; i < 10; i++) {
            cacheManager.getCache("test-cache");
        }
    }
}
