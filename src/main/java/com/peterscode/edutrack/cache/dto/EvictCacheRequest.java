package com.peterscode.edutrack.cache.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for cache eviction by school.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvictCacheRequest {

    /** The school whose cache should be evicted. */
    @NotNull(message = "School ID is required")
    private Long schoolId;

    /** Optional cache name to evict; if null, all caches for the school are evicted. */
    private String cacheName;
}
