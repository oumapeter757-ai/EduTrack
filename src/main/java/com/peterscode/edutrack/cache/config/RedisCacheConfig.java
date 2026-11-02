package com.peterscode.edutrack.cache.config;

import com.peterscode.edutrack.cache.CacheEvictionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache infrastructure for EduTrack.
 * <p>
 * Activated only when {@code edutrack.cache.enabled=true}. When disabled,
 * a no-op {@link org.springframework.cache.support.NoOpCacheManager} is used
 * and all @Cacheable calls fall through to the database.
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "edutrack.cache", name = "enabled", havingValue = "true")
public class RedisCacheConfig {

    private final EdutrackCacheProperties properties;
    private final RedisConnectionFactory connectionFactory;

    @Bean
    @Primary
    public CacheManager redisCacheManager() {
        EduTrackRedisSerializer valueSerializer = new EduTrackRedisSerializer();
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        RedisSerializationContext.SerializationPair<String> keyPair =
                RedisSerializationContext.SerializationPair.fromSerializer(keySerializer);
        RedisSerializationContext.SerializationPair<Object> valuePair =
                RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer);

        // Build per-cache configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        for (Map.Entry<String, Duration> entry : properties.getTtl().entrySet()) {
            String cacheName = entry.getKey();
            Duration ttl = entry.getValue();
            cacheConfigurations.put(cacheName, buildCacheConfiguration(ttl, keyPair, valuePair));
        }

        // Default configuration for caches not explicitly listed
        RedisCacheConfiguration defaultConfig = buildCacheConfiguration(
                properties.getDefaultTtl(), keyPair, valuePair);

        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

        RedisCacheManager cacheManager = RedisCacheManager.RedisCacheManagerBuilder.fromCacheWriter(writer)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();

        log.info("Redis cache manager initialized with {} cache configurations", cacheConfigurations.size());
        return cacheManager;
    }

    private RedisCacheConfiguration buildCacheConfiguration(
            Duration ttl,
            RedisSerializationContext.SerializationPair<String> keyPair,
            RedisSerializationContext.SerializationPair<Object> valuePair) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(keyPair)
                .serializeValuesWith(valuePair)
                .disableCachingNullValues();
    }

    /**
     * Error handler that logs a short warning and lets the call fall through to the database.
     * Never logs keys with personal data, payload content, or the Redis password.
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler(MeterRegistry meterRegistry) {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                String cacheName = cache.getName();
                String errorType = exception.getClass().getSimpleName();
                log.warn("Cache get error in '{}': {}", cacheName, errorType);
                meterRegistry.counter("edutrack.cache.error", "cache", cacheName, "operation", "get", "error", errorType).increment();
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                String cacheName = cache.getName();
                String errorType = exception.getClass().getSimpleName();
                log.warn("Cache put error in '{}': {}", cacheName, errorType);
                meterRegistry.counter("edutrack.cache.error", "cache", cacheName, "operation", "put", "error", errorType).increment();
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                String cacheName = cache.getName();
                String errorType = exception.getClass().getSimpleName();
                log.warn("Cache evict error in '{}': {}", cacheName, errorType);
                meterRegistry.counter("edutrack.cache.error", "cache", cacheName, "operation", "evict", "error", errorType).increment();
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                String cacheName = cache.getName();
                String errorType = exception.getClass().getSimpleName();
                log.warn("Cache clear error in '{}': {}", cacheName, errorType);
                meterRegistry.counter("edutrack.cache.error", "cache", cacheName, "operation", "clear", "error", errorType).increment();
            }
        };
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new EduTrackRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new EduTrackRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheEvictionService cacheEvictionService(RedisTemplate<String, Object> redisTemplate, CacheKeys cacheKeys) {
        return new CacheEvictionService(redisTemplate, cacheKeys);
    }
}
