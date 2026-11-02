package com.peterscode.edutrack.cache.controller;

import com.peterscode.edutrack.cache.CacheEvictionService;
import com.peterscode.edutrack.cache.dto.EvictCacheRequest;
import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.security.RequiresActiveSchool;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin controller for Redis cache management.
 * <p>
 * Only SUPER_ADMIN can evict caches. Every eviction is audited via logging.
 * No endpoint exposes cache contents.
 */
@Slf4j
@RestController
@NoModuleRequired
@RequiresActiveSchool
@RequestMapping("/api/v1/admin/cache")
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "edutrack.cache", name = "enabled", havingValue = "true")
public class CacheAdminController {

    private final CacheEvictionService cacheEvictionService;

    /**
     * Evict all caches for a specific school.
     * <p>
     * SUPER_ADMIN only. Audited via log.
     *
     * @param request contains the schoolId and optional cacheName
     * @param currentUser the authenticated super admin
     * @return number of keys evicted
     */
    @PostMapping("/evict")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> evictCache(
            @Valid @RequestBody EvictCacheRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        long evicted;
        if (request.getCacheName() != null && !request.getCacheName().isBlank()) {
            evicted = cacheEvictionService.evictCacheForSchool(request.getCacheName(), request.getSchoolId());
        } else {
            evicted = cacheEvictionService.evictAllForSchool(request.getSchoolId());
        }

        // Audit log
        log.info("Cache eviction by SUPER_ADMIN user={} schoolId={} cacheName={} keysEvicted={}",
                currentUser.getEmail(), request.getSchoolId(), request.getCacheName(), evicted);

        Map<String, Object> data = Map.of(
                "schoolId", request.getSchoolId(),
                "cacheName", request.getCacheName() != null ? request.getCacheName() : "all",
                "keysEvicted", evicted
        );

        return ResponseEntity.ok(ApiResponse.success("Cache evicted successfully", data));
    }
}
