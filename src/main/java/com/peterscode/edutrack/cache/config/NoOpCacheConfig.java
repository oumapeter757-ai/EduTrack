package com.peterscode.edutrack.cache.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * No-op cache configuration when Redis caching is disabled.
 * All @Cacheable calls fall through to the database.
 */
@Slf4j
@Configuration
@EnableCaching
@ConditionalOnProperty(prefix = "edutrack.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpCacheConfig {

    @Bean
    @Primary
    public CacheManager noOpCacheManager() {
        log.info("Redis caching is disabled — using NoOpCacheManager");
        return new org.springframework.cache.support.NoOpCacheManager();
    }
}
