package com.peterscode.edutrack.cache.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

/**
 * Redis JSON serializer for EduTrack caching.
 * <p>
 * Uses Jackson 3 with strict type handling:
 * <ul>
 *   <li>NO native Java serialization</li>
 *   <li>NO unrestricted polymorphic default typing (deserialization gadget risk)</li>
 *   <li>Deserialization errors are treated as cache misses via CacheErrorHandler</li>
 * </ul>
 * <p>
 * Extends {@link GenericJackson2JsonRedisSerializer} to reuse Spring Data Redis's
 * type-id mechanism while hardening the ObjectMapper.
 */
public class EduTrackRedisSerializer extends GenericJackson2JsonRedisSerializer {

    public EduTrackRedisSerializer() {
        super(createObjectMapper());
    }

    private static ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .addModule(new JavaTimeModule())
                .build();
    }
}
